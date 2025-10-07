/*
 * client_main.c
 * Purpose: C UDP client for facility booking system. Demonstrates heterogeneous RPC with Java server.
 * Design notes:
 * - Uses Winsock2 on Windows or POSIX sockets on Linux/macOS.
 * - Implements at-least-once retry logic with timeout.
 * - Supports query, book, change, and custom operations.
 * - Manual marshalling with wire_codec functions ensures correct byte order.
 */

#include "protocol.h"
#include "wire_codec.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* Platform-specific socket headers */
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #pragma comment(lib, "ws2_32.lib")
    typedef int socklen_t;
    #define close closesocket
#else
    #include <unistd.h>
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <sys/select.h>
    typedef int SOCKET;
    #define INVALID_SOCKET -1
    #define SOCKET_ERROR -1
#endif

/* Default config */
#define DEFAULT_HOST "127.0.0.1"
#define DEFAULT_PORT 9999
#define DEFAULT_TIMEOUT_MS 500
#define DEFAULT_RETRIES 3
#define MAX_DGRAM_SIZE 65536

/* Global request ID counter (simple monotonic) */
static uint32_t g_request_id = 0;

/*
 * Initialize request ID with random seed.
 */
void init_request_id() {
    srand((unsigned)time(NULL));                         /* seed RNG with current time */
    g_request_id = (uint32_t)(rand() & 0x3FFFFFFF);      /* start with random value */
}

/*
 * Get next request ID (monotonic increment).
 */
uint32_t next_request_id() {
    return ++g_request_id;                               /* increment and return */
}

/*
 * Send UDP datagram and wait for response with timeout and retries.
 * Returns number of bytes received, or -1 on failure after all retries.
 */
int udp_invoke(SOCKET sock, const struct sockaddr_in *server_addr,
               const uint8_t *request, size_t req_len,
               uint8_t *response, size_t resp_max,
               int timeout_ms, int max_retries) {
    for (int attempt = 0; attempt <= max_retries; attempt++) {
        /* Send request datagram */
        int sent = sendto(sock, (const char*)request, (int)req_len, 0,
                          (struct sockaddr*)server_addr, sizeof(*server_addr));
        if (sent < 0) {
            perror("sendto failed");                     /* log error */
            continue;                                    /* retry */
        }

        /* Wait for response with timeout using select */
        fd_set readfds;                                  /* file descriptor set */
        FD_ZERO(&readfds);                               /* clear set */
        FD_SET(sock, &readfds);                          /* add socket to set */
        struct timeval tv;                               /* timeout value */
        tv.tv_sec = timeout_ms / 1000;                   /* seconds part */
        tv.tv_usec = (timeout_ms % 1000) * 1000;         /* microseconds part */

        int sel = select((int)(sock + 1), &readfds, NULL, NULL, &tv); /* wait for readable */
        if (sel < 0) {
            perror("select failed");                     /* log error */
            return -1;                                   /* abort */
        } else if (sel == 0) {
            printf("[retry %d/%d] timeout, retrying...\n", attempt + 1, max_retries + 1);
            continue;                                    /* timeout, retry */
        }

        /* Receive response datagram */
        struct sockaddr_in from_addr;                    /* sender address */
        socklen_t from_len = sizeof(from_addr);          /* address length */
        int recv_len = recvfrom(sock, (char*)response, (int)resp_max, 0,
                                (struct sockaddr*)&from_addr, &from_len);
        if (recv_len < 0) {
            perror("recvfrom failed");                   /* log error */
            return -1;                                   /* abort */
        }
        return recv_len;                                 /* success, return length */
    }
    fprintf(stderr, "Failed after %d retries\n", max_retries + 1); /* all retries exhausted */
    return -1;                                           /* failure */
}

/*
 * Command: query facility availability for a given day.
 * Usage: query --facility LabA --date 2025-10-10
 */
void cmd_query(SOCKET sock, struct sockaddr_in *server_addr, const char *facility,
               const char *date_str, int timeout_ms, int retries, int at_most_once) {
    /* Parse date string (yyyy-MM-dd) to epoch milliseconds (simplified: assume start of day UTC) */
    /* For demo, we use a fixed timestamp; production would parse date properly */
    int64_t day_start = 1728518400000LL;                 /* example: 2024-10-10 00:00:00 UTC in ms */
    int64_t day_end = day_start + 86400000LL;            /* +24 hours */

    /* Build request payload: string facility + i64 dayStart + i64 dayEnd */
    uint8_t req_buf[MAX_DGRAM_SIZE];                     /* request buffer */
    int offset = HEADER_LEN;                             /* skip header, fill payload first */
    offset += write_string(req_buf + offset, facility);  /* write facility string */
    offset += write_i64(req_buf + offset, day_start);    /* write day start timestamp */
    offset += write_i64(req_buf + offset, day_end);      /* write day end timestamp */
    int payload_len = offset - HEADER_LEN;               /* compute payload length */

    /* Build header */
    Header hdr;                                          /* header struct */
    hdr.version = PROTOCOL_VERSION;                      /* set version */
    hdr.opCode = OP_QUERY_AVAIL;                         /* query op */
    hdr.requestId = next_request_id();                   /* allocate request id */
    hdr.flags = at_most_once ? FLAG_AT_MOST_ONCE : 0;    /* set flags */
    hdr.payloadLen = payload_len;                        /* payload length */
    write_header(req_buf, &hdr);                         /* write header at offset 0 */

    /* Send request and receive response */
    uint8_t resp_buf[MAX_DGRAM_SIZE];                    /* response buffer */
    int resp_len = udp_invoke(sock, server_addr, req_buf, offset, resp_buf, sizeof(resp_buf), timeout_ms, retries);
    if (resp_len < 0) {
        fprintf(stderr, "Query failed\n");               /* error */
        return;
    }

    /* Parse response header */
    Header resp_hdr;                                     /* response header */
    read_header(resp_buf, &resp_hdr);                    /* read header */
    if (resp_hdr.opCode & OP_ERROR_MASK) {               /* check error bit */
        fprintf(stderr, "Server error response\n");      /* error */
        return;
    }

    /* Parse intervals: uint16 count + [i64 start, i64 end]* */
    int pos = HEADER_LEN;                                /* payload start */
    uint16_t count;                                      /* intervals count */
    pos += read_u16(resp_buf + pos, &count);             /* read count */
    printf("Available intervals: %u\n", count);          /* print count */
    for (int i = 0; i < count; i++) {                    /* loop intervals */
        int64_t start, end;                              /* interval bounds */
        pos += read_i64(resp_buf + pos, &start);         /* read start */
        pos += read_i64(resp_buf + pos, &end);           /* read end */
        printf("  [%lld, %lld]\n", (long long)start, (long long)end); /* print interval */
    }
}

/*
 * Command: book a facility.
 * Usage: book --facility LabA --user alice --start <ms> --end <ms>
 */
void cmd_book(SOCKET sock, struct sockaddr_in *server_addr, const char *facility,
              const char *user, int64_t start_ms, int64_t end_ms,
              int timeout_ms, int retries, int at_most_once) {
    /* Build request payload: str facility + str user + i64 start + i64 end */
    uint8_t req_buf[MAX_DGRAM_SIZE];                     /* request buffer */
    int offset = HEADER_LEN;                             /* skip header */
    offset += write_string(req_buf + offset, facility);  /* facility */
    offset += write_string(req_buf + offset, user);      /* user */
    offset += write_i64(req_buf + offset, start_ms);     /* start timestamp */
    offset += write_i64(req_buf + offset, end_ms);       /* end timestamp */
    int payload_len = offset - HEADER_LEN;               /* payload length */

    /* Build header */
    Header hdr;                                          /* header */
    hdr.version = PROTOCOL_VERSION;                      /* version */
    hdr.opCode = OP_BOOK;                                /* book op */
    hdr.requestId = next_request_id();                   /* request id */
    hdr.flags = at_most_once ? FLAG_AT_MOST_ONCE : 0;    /* flags */
    hdr.payloadLen = payload_len;                        /* payload len */
    write_header(req_buf, &hdr);                         /* write header */

    /* Send and receive */
    uint8_t resp_buf[MAX_DGRAM_SIZE];                    /* response buffer */
    int resp_len = udp_invoke(sock, server_addr, req_buf, offset, resp_buf, sizeof(resp_buf), timeout_ms, retries);
    if (resp_len < 0) {
        fprintf(stderr, "Book failed\n");                /* error */
        return;
    }

    /* Parse response: i64 bookingId */
    Header resp_hdr;                                     /* response header */
    read_header(resp_buf, &resp_hdr);                    /* read header */
    if (resp_hdr.opCode & OP_ERROR_MASK) {               /* check error */
        fprintf(stderr, "Server error response\n");      /* error */
        return;
    }
    int64_t booking_id;                                  /* booking id */
    read_i64(resp_buf + HEADER_LEN, &booking_id);        /* read id */
    printf("Booking created: id=%lld\n", (long long)booking_id); /* print result */
}

/*
 * Command: increment facility usage counter (non-idempotent custom op).
 * Usage: custom-incr --facility LabA
 */
void cmd_custom_incr(SOCKET sock, struct sockaddr_in *server_addr, const char *facility,
                     int timeout_ms, int retries, int at_most_once) {
    /* Build request payload: str facility */
    uint8_t req_buf[MAX_DGRAM_SIZE];                     /* request buffer */
    int offset = HEADER_LEN;                             /* skip header */
    offset += write_string(req_buf + offset, facility);  /* facility */
    int payload_len = offset - HEADER_LEN;               /* payload length */

    /* Build header */
    Header hdr;                                          /* header */
    hdr.version = PROTOCOL_VERSION;                      /* version */
    hdr.opCode = OP_CUSTOM_NON_IDEMPOTENT;               /* non-idempotent op */
    hdr.requestId = next_request_id();                   /* request id */
    hdr.flags = at_most_once ? FLAG_AT_MOST_ONCE : 0;    /* flags */
    hdr.payloadLen = payload_len;                        /* payload len */
    write_header(req_buf, &hdr);                         /* write header */

    /* Send and receive */
    uint8_t resp_buf[MAX_DGRAM_SIZE];                    /* response buffer */
    int resp_len = udp_invoke(sock, server_addr, req_buf, offset, resp_buf, sizeof(resp_buf), timeout_ms, retries);
    if (resp_len < 0) {
        fprintf(stderr, "Usage counter increment failed\n"); /* error */
        return;
    }

    /* Parse response: i64 counter value */
    Header resp_hdr;                                     /* response header */
    read_header(resp_buf, &resp_hdr);                    /* read header */
    if (resp_hdr.opCode & OP_ERROR_MASK) {               /* check error */
        fprintf(stderr, "Server error response\n");      /* error */
        return;
    }
    int64_t usage_count;                                 /* usage counter value */
    read_i64(resp_buf + HEADER_LEN, &usage_count);       /* read usage count */
    printf("Usage counter for facility=%s => %lld\n", facility, (long long)usage_count); /* print result */
}

/*
 * Command: change booking time.
 * Usage: change --booking-id 1 --offset 60
 */
void cmd_change(SOCKET sock, struct sockaddr_in *server_addr, int64_t booking_id,
                int offset_minutes, int timeout_ms, int retries, int at_most_once) {
    /* Build request payload: i64 bookingId + u32 offsetMinutes */
    uint8_t req_buf[MAX_DGRAM_SIZE];                     /* request buffer */
    int offset = HEADER_LEN;                             /* skip header */
    write_i64(req_buf + offset, booking_id);             /* booking id */
    offset += 8;                                         /* advance */
    write_u32(req_buf + offset, (uint32_t)offset_minutes); /* offset minutes */
    offset += 4;                                         /* advance */
    int payload_len = offset - HEADER_LEN;               /* payload length */

    /* Build header */
    Header hdr;                                          /* header */
    hdr.version = PROTOCOL_VERSION;                      /* version */
    hdr.opCode = OP_CHANGE_BOOKING;                      /* change booking op */
    hdr.requestId = next_request_id();                   /* request id */
    hdr.flags = at_most_once ? FLAG_AT_MOST_ONCE : 0;    /* flags */
    hdr.payloadLen = payload_len;                        /* payload len */
    write_header(req_buf, &hdr);                         /* write header */

    /* Send and receive */
    uint8_t resp_buf[MAX_DGRAM_SIZE];                    /* response buffer */
    int resp_len = udp_invoke(sock, server_addr, req_buf, offset, resp_buf, sizeof(resp_buf), timeout_ms, retries);
    if (resp_len < 0) {
        fprintf(stderr, "Change booking failed\n");      /* error */
        return;
    }

    /* Parse response: i64 start + i64 end */
    Header resp_hdr;                                     /* response header */
    read_header(resp_buf, &resp_hdr);                    /* read header */
    if (resp_hdr.opCode & OP_ERROR_MASK) {               /* check error */
        fprintf(stderr, "Server error response\n");      /* error */
        return;
    }
    int64_t new_start, new_end;                          /* new time range */
    read_i64(resp_buf + HEADER_LEN, &new_start);         /* read start */
    read_i64(resp_buf + HEADER_LEN + 8, &new_end);       /* read end */
    printf("Booking changed: new time [%lld, %lld]\n", (long long)new_start, (long long)new_end); /* print result */
}

/*
 * Command: register monitor for facility changes.
 * Usage: monitor --facility LabA --duration 30 --callback-port 10000
 */
void cmd_monitor(SOCKET sock, struct sockaddr_in *server_addr, const char *facility,
                 uint32_t duration_seconds, uint32_t callback_port,
                 int timeout_ms, int retries, int at_most_once) {
    /* Build request payload: str facility + u32 windowSeconds + u32 callbackPort */
    uint8_t req_buf[MAX_DGRAM_SIZE];                     /* request buffer */
    int offset = HEADER_LEN;                             /* skip header */
    offset += write_string(req_buf + offset, facility);  /* facility */
    write_u32(req_buf + offset, duration_seconds);       /* window seconds */
    offset += 4;                                         /* advance */
    write_u32(req_buf + offset, callback_port);          /* callback port */
    offset += 4;                                         /* advance */
    int payload_len = offset - HEADER_LEN;               /* payload length */

    /* Build header */
    Header hdr;                                          /* header */
    hdr.version = PROTOCOL_VERSION;                      /* version */
    hdr.opCode = OP_MONITOR;                             /* monitor op */
    hdr.requestId = next_request_id();                   /* request id */
    hdr.flags = at_most_once ? FLAG_AT_MOST_ONCE : 0;    /* flags */
    hdr.payloadLen = payload_len;                        /* payload len */
    write_header(req_buf, &hdr);                         /* write header */

    /* Send and receive */
    uint8_t resp_buf[MAX_DGRAM_SIZE];                    /* response buffer */
    int resp_len = udp_invoke(sock, server_addr, req_buf, offset, resp_buf, sizeof(resp_buf), timeout_ms, retries);
    if (resp_len < 0) {
        fprintf(stderr, "Monitor registration failed\n"); /* error */
        return;
    }

    /* Parse response: u16 ok */
    Header resp_hdr;                                     /* response header */
    read_header(resp_buf, &resp_hdr);                    /* read header */
    if (resp_hdr.opCode & OP_ERROR_MASK) {               /* check error */
        fprintf(stderr, "Server error response\n");      /* error */
        return;
    }
    uint16_t ok;                                         /* ok flag */
    read_u16(resp_buf + HEADER_LEN, &ok);                /* read ok */
    if (ok == 1) {
        printf("Monitor registered for facility=%s, duration=%u seconds, callback port=%u\n",
               facility, duration_seconds, callback_port); /* success */
        printf("Listening for callbacks on port %u...\n", callback_port);
        
        /* Now listen for callbacks on the callback port */
        SOCKET callback_sock = socket(AF_INET, SOCK_DGRAM, 0); /* create callback socket */
        if (callback_sock == INVALID_SOCKET) {
            perror("callback socket creation failed");    /* error */
            return;
        }
        
        /* Bind to callback port */
        struct sockaddr_in callback_addr;                /* callback address */
        memset(&callback_addr, 0, sizeof(callback_addr)); /* zero out */
        callback_addr.sin_family = AF_INET;              /* IPv4 */
        callback_addr.sin_addr.s_addr = INADDR_ANY;      /* any interface */
        callback_addr.sin_port = htons(callback_port);   /* callback port */
        
        if (bind(callback_sock, (struct sockaddr*)&callback_addr, sizeof(callback_addr)) < 0) {
            perror("callback socket bind failed");       /* error */
            close(callback_sock);                        /* close socket */
            return;
        }
        
        printf("Waiting for callbacks (press Ctrl+C to stop)...\n");
        
        /* Wait for callbacks in a loop */
        while (1) {
            uint8_t callback_buf[MAX_DGRAM_SIZE];        /* callback buffer */
            struct sockaddr_in from_addr;                /* sender address */
            socklen_t from_len = sizeof(from_addr);      /* address length */
            
            int recv_len = recvfrom(callback_sock, (char*)callback_buf, sizeof(callback_buf), 0,
                                    (struct sockaddr*)&from_addr, &from_len);
            if (recv_len < 0) {
                perror("recvfrom failed");               /* error */
                break;
            }
            
            /* Parse callback message */
            Header cb_hdr;                               /* callback header */
            read_header(callback_buf, &cb_hdr);          /* read header */
            
            printf("\n=== Callback received ===\n");
            printf("OpCode: 0x%04x, RequestId: %u, Flags: 0x%x\n",
                   cb_hdr.opCode, cb_hdr.requestId, cb_hdr.flags);
            
            /* Parse callback payload (same as QUERY_AVAIL response) */
            if (cb_hdr.opCode == OP_QUERY_AVAIL) {
                uint16_t count;                          /* interval count */
                read_u16(callback_buf + HEADER_LEN, &count); /* read count */
                printf("Facility availability updated: %u intervals\n", count);
                
                int offset_cb = HEADER_LEN + 2;         /* start after count */
                for (int i = 0; i < count; i++) {
                    int64_t start, end;                  /* interval */
                    read_i64(callback_buf + offset_cb, &start);
                    offset_cb += 8;
                    read_i64(callback_buf + offset_cb, &end);
                    offset_cb += 8;
                    printf("  [%lld, %lld]\n", (long long)start, (long long)end);
                }
            }
            printf("========================\n");
        }
        
        close(callback_sock);                            /* close callback socket */
    } else {
        fprintf(stderr, "Monitor registration failed (ok=%u)\n", ok); /* error */
    }
}

/*
 * Command: reset facility schedule for a specific day (idempotent custom op).
 * Usage: reset --facility LabA --day-start 1728518400000 --day-end 1728604800000
 */
void cmd_reset(SOCKET sock, struct sockaddr_in *server_addr, const char *facility,
               int64_t day_start, int64_t day_end, int timeout_ms, int retries, int at_most_once) {
    /* Build request payload: str facility + i64 dayStart + i64 dayEnd */
    uint8_t req_buf[MAX_DGRAM_SIZE];                     /* request buffer */
    int offset = HEADER_LEN;                             /* skip header */
    offset += write_string(req_buf + offset, facility);  /* facility */
    write_i64(req_buf + offset, day_start);              /* day start */
    offset += 8;                                         /* advance */
    write_i64(req_buf + offset, day_end);                /* day end */
    offset += 8;                                         /* advance */
    int payload_len = offset - HEADER_LEN;               /* payload length */

    /* Build header */
    Header hdr;                                          /* header */
    hdr.version = PROTOCOL_VERSION;                      /* version */
    hdr.opCode = OP_CUSTOM_IDEMPOTENT;                   /* idempotent op */
    hdr.requestId = next_request_id();                   /* request id */
    hdr.flags = at_most_once ? FLAG_AT_MOST_ONCE : 0;    /* flags */
    hdr.payloadLen = payload_len;                        /* payload len */
    write_header(req_buf, &hdr);                         /* write header */

    /* Send and receive */
    uint8_t resp_buf[MAX_DGRAM_SIZE];                    /* response buffer */
    int resp_len = udp_invoke(sock, server_addr, req_buf, offset, resp_buf, sizeof(resp_buf), timeout_ms, retries);
    if (resp_len < 0) {
        fprintf(stderr, "Schedule reset failed\n");      /* error */
        return;
    }

    /* Parse response: u32 removed count */
    Header resp_hdr;                                     /* response header */
    read_header(resp_buf, &resp_hdr);                    /* read header */
    if (resp_hdr.opCode & OP_ERROR_MASK) {               /* check error */
        fprintf(stderr, "Server error response\n");      /* error */
        return;
    }
    uint32_t removed_count;                              /* removed bookings count */
    read_u32(resp_buf + HEADER_LEN, &removed_count);     /* read count */
    printf("Schedule reset for facility=%s: %u booking(s) removed\n", facility, removed_count); /* print result */
}

/*
 * Main entry point: parse CLI args and dispatch commands.
 */
int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <query|book|change|monitor|reset|custom-incr> [options]\n", argv[0]);
        return 1;
    }

    /* Parse command and options */
    const char *cmd = argv[1];                           /* command name */
    const char *host = DEFAULT_HOST;                     /* server host */
    int port = DEFAULT_PORT;                             /* server port */
    int timeout_ms = DEFAULT_TIMEOUT_MS;                 /* timeout */
    int retries = DEFAULT_RETRIES;                       /* max retries */
    int at_most_once = 0;                                /* at-most-once flag */
    const char *facility = "LabA";                       /* facility name */
    const char *user = "alice";                          /* user name */
    const char *date_str = "2025-10-10";                 /* date string */
    int64_t start_ms = 1728540000000LL;                  /* example start timestamp */
    int64_t end_ms = 1728543600000LL;                    /* example end timestamp */
    int64_t day_start = 1728518400000LL;                 /* day start for reset (00:00) */
    int64_t day_end = 1728604800000LL;                   /* day end for reset (24:00) */
    int64_t booking_id = 1;                              /* booking id for change */
    int offset_minutes = 60;                             /* offset minutes for change */
    uint32_t duration_seconds = 30;                      /* monitor duration */
    uint32_t callback_port = 10000;                      /* callback port for monitor */

    /* Simple argument parsing loop */
    for (int i = 2; i < argc; i++) {
        if (strcmp(argv[i], "--host") == 0 && i + 1 < argc) {
            host = argv[++i];                            /* set host */
        } else if (strcmp(argv[i], "--port") == 0 && i + 1 < argc) {
            port = atoi(argv[++i]);                      /* set port */
        } else if (strcmp(argv[i], "--facility") == 0 && i + 1 < argc) {
            facility = argv[++i];                        /* set facility */
        } else if (strcmp(argv[i], "--user") == 0 && i + 1 < argc) {
            user = argv[++i];                            /* set user */
        } else if (strcmp(argv[i], "--date") == 0 && i + 1 < argc) {
            date_str = argv[++i];                        /* set date */
        } else if (strcmp(argv[i], "--start") == 0 && i + 1 < argc) {
            start_ms = atoll(argv[++i]);                 /* set start ms */
        } else if (strcmp(argv[i], "--end") == 0 && i + 1 < argc) {
            end_ms = atoll(argv[++i]);                   /* set end ms */
        } else if (strcmp(argv[i], "--day-start") == 0 && i + 1 < argc) {
            day_start = atoll(argv[++i]);                /* set day start */
        } else if (strcmp(argv[i], "--day-end") == 0 && i + 1 < argc) {
            day_end = atoll(argv[++i]);                  /* set day end */
        } else if (strcmp(argv[i], "--booking-id") == 0 && i + 1 < argc) {
            booking_id = atoll(argv[++i]);               /* set booking id */
        } else if (strcmp(argv[i], "--offset") == 0 && i + 1 < argc) {
            offset_minutes = atoi(argv[++i]);            /* set offset minutes */
        } else if (strcmp(argv[i], "--duration") == 0 && i + 1 < argc) {
            duration_seconds = (uint32_t)atoi(argv[++i]); /* set duration */
        } else if (strcmp(argv[i], "--callback-port") == 0 && i + 1 < argc) {
            callback_port = (uint32_t)atoi(argv[++i]);   /* set callback port */
        } else if (strcmp(argv[i], "--timeoutMs") == 0 && i + 1 < argc) {
            timeout_ms = atoi(argv[++i]);                /* set timeout */
        } else if (strcmp(argv[i], "--retries") == 0 && i + 1 < argc) {
            retries = atoi(argv[++i]);                   /* set retries */
        } else if (strcmp(argv[i], "--atMostOnce") == 0 && i + 1 < argc) {
            at_most_once = atoi(argv[++i]);              /* set at-most-once flag */
        }
    }

    /* Initialize Winsock on Windows */
#ifdef _WIN32
    WSADATA wsa_data;                                    /* Winsock data */
    if (WSAStartup(MAKEWORD(2, 2), &wsa_data) != 0) {    /* initialize Winsock 2.2 */
        fprintf(stderr, "WSAStartup failed\n");          /* log error */
        return 1;
    }
#endif

    /* Initialize request ID counter */
    init_request_id();                                   /* seed RNG and init counter */

    /* Create UDP socket */
    SOCKET sock = socket(AF_INET, SOCK_DGRAM, 0);        /* create UDP socket */
    if (sock == INVALID_SOCKET) {
        perror("socket creation failed");                /* log error */
#ifdef _WIN32
        WSACleanup();                                    /* cleanup Winsock */
#endif
        return 1;
    }

    /* Setup server address */
    struct sockaddr_in server_addr;                      /* server address struct */
    memset(&server_addr, 0, sizeof(server_addr));        /* zero out struct */
    server_addr.sin_family = AF_INET;                    /* IPv4 */
    server_addr.sin_port = htons(port);                  /* port in network byte order */
    if (inet_pton(AF_INET, host, &server_addr.sin_addr) <= 0) { /* convert IP string to binary */
        perror("invalid server address");                /* log error */
        close(sock);                                     /* close socket */
#ifdef _WIN32
        WSACleanup();                                    /* cleanup Winsock */
#endif
        return 1;
    }

    /* Dispatch command */
    if (strcmp(cmd, "query") == 0) {
        cmd_query(sock, &server_addr, facility, date_str, timeout_ms, retries, at_most_once);
    } else if (strcmp(cmd, "book") == 0) {
        cmd_book(sock, &server_addr, facility, user, start_ms, end_ms, timeout_ms, retries, at_most_once);
    } else if (strcmp(cmd, "change") == 0) {
        cmd_change(sock, &server_addr, booking_id, offset_minutes, timeout_ms, retries, at_most_once);
    } else if (strcmp(cmd, "monitor") == 0) {
        cmd_monitor(sock, &server_addr, facility, duration_seconds, callback_port, timeout_ms, retries, at_most_once);
    } else if (strcmp(cmd, "reset") == 0) {
        cmd_reset(sock, &server_addr, facility, day_start, day_end, timeout_ms, retries, at_most_once);
    } else if (strcmp(cmd, "custom-incr") == 0) {
        cmd_custom_incr(sock, &server_addr, facility, timeout_ms, retries, at_most_once);
    } else {
        fprintf(stderr, "Unknown command: %s\n", cmd);   /* unknown command */
    }

    /* Cleanup */
    close(sock);                                         /* close socket */
#ifdef _WIN32
    WSACleanup();                                        /* cleanup Winsock */
#endif
    return 0;
}
