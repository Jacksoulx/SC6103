/*
 * wire_codec.c
 * Purpose: Implementation of manual marshalling/unmarshalling for C client.
 * Design notes:
 * - Uses htons/htonl/ntohs/ntohl for 16/32-bit conversions.
 * - For 64-bit values, manually split into two 32-bit halves (high/low) and convert each.
 * - All writes advance the buffer pointer; caller manages buffer size.
 */

#include "wire_codec.h"
#include <string.h>

/* Platform-specific network byte order headers */
#ifdef _WIN32
    #include <winsock2.h>
#else
    #include <arpa/inet.h>  /* htons, htonl, ntohs, ntohl */
#endif

/* Write header: 16 bytes total */
int write_header(uint8_t *buf, const Header *h) {
    int offset = 0;                                      /* current write position */
    offset += write_u16(buf + offset, h->version);       /* write version uint16 */
    offset += write_u16(buf + offset, h->opCode);        /* write opCode uint16 */
    offset += write_u32(buf + offset, h->requestId);     /* write requestId uint32 */
    offset += write_u32(buf + offset, h->flags);         /* write flags uint32 */
    offset += write_u32(buf + offset, h->payloadLen);    /* write payloadLen uint32 */
    return offset;                                       /* should be 16 */
}

/* Read header: 16 bytes total */
int read_header(const uint8_t *buf, Header *h) {
    int offset = 0;                                      /* current read position */
    offset += read_u16(buf + offset, &h->version);       /* read version */
    offset += read_u16(buf + offset, &h->opCode);        /* read opCode */
    offset += read_u32(buf + offset, &h->requestId);     /* read requestId */
    offset += read_u32(buf + offset, &h->flags);         /* read flags */
    offset += read_u32(buf + offset, &h->payloadLen);    /* read payloadLen */
    return offset;                                       /* should be 16 */
}

/* Write uint16 in network byte order */
int write_u16(uint8_t *buf, uint16_t val) {
    uint16_t net = htons(val);                           /* convert to big-endian */
    memcpy(buf, &net, 2);                                /* copy 2 bytes */
    return 2;                                            /* bytes written */
}

/* Write uint32 in network byte order */
int write_u32(uint8_t *buf, uint32_t val) {
    uint32_t net = htonl(val);                           /* convert to big-endian */
    memcpy(buf, &net, 4);                                /* copy 4 bytes */
    return 4;                                            /* bytes written */
}

/* Write int64 in network byte order (split into two uint32) */
int write_i64(uint8_t *buf, int64_t val) {
    uint32_t high = (uint32_t)((val >> 32) & 0xFFFFFFFFUL); /* upper 32 bits */
    uint32_t low  = (uint32_t)(val & 0xFFFFFFFFUL);         /* lower 32 bits */
    int offset = 0;                                      /* write position */
    offset += write_u32(buf + offset, high);             /* write high word first (BE) */
    offset += write_u32(buf + offset, low);              /* write low word */
    return offset;                                       /* 8 bytes */
}

/* Read uint16 from network byte order */
int read_u16(const uint8_t *buf, uint16_t *val) {
    uint16_t net;                                        /* network byte order holder */
    memcpy(&net, buf, 2);                                /* copy 2 bytes */
    *val = ntohs(net);                                   /* convert to host order */
    return 2;                                            /* bytes read */
}

/* Read uint32 from network byte order */
int read_u32(const uint8_t *buf, uint32_t *val) {
    uint32_t net;                                        /* network byte order holder */
    memcpy(&net, buf, 4);                                /* copy 4 bytes */
    *val = ntohl(net);                                   /* convert to host order */
    return 4;                                            /* bytes read */
}

/* Read int64 from network byte order (two uint32) */
int read_i64(const uint8_t *buf, int64_t *val) {
    uint32_t high, low;                                  /* high and low words */
    int offset = 0;                                      /* read position */
    offset += read_u32(buf + offset, &high);             /* read high word */
    offset += read_u32(buf + offset, &low);              /* read low word */
    *val = ((int64_t)high << 32) | (int64_t)low;         /* combine into 64-bit */
    return offset;                                       /* 8 bytes */
}

/* Write length-prefixed string */
int write_string(uint8_t *buf, const char *str) {
    size_t len = strlen(str);                            /* compute string length */
    if (len > 0xFFFF) len = 0xFFFF;                      /* clamp to uint16 max */
    int offset = 0;                                      /* write position */
    offset += write_u16(buf + offset, (uint16_t)len);    /* write length prefix */
    memcpy(buf + offset, str, len);                      /* copy string bytes */
    offset += len;                                       /* advance by string length */
    return offset;                                       /* total bytes written */
}

/* Read length-prefixed string */
int read_string(const uint8_t *buf, char *out, size_t max_len) {
    uint16_t len;                                        /* string length from wire */
    int offset = 0;                                      /* read position */
    offset += read_u16(buf + offset, &len);              /* read length prefix */
    if (len >= max_len) return -1;                       /* buffer too small */
    memcpy(out, buf + offset, len);                      /* copy string bytes */
    out[len] = '\0';                                     /* null-terminate */
    offset += len;                                       /* advance by string length */
    return offset;                                       /* total bytes read */
}
