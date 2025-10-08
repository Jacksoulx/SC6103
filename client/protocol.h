/*
 * protocol.h
 * Purpose: C header defining UDP wire protocol constants, op codes, and header structure.
 * Design notes:
 * - Must match Java Protocol.java exactly for interoperability.
 * - All multi-byte integers on wire are big-endian (network byte order).
 * - Use htons/htonl/ntohs/ntohl for conversions.
 * - Header is exactly 16 bytes; no padding allowed.
 */

#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <stdint.h>

/* Protocol version */
#define PROTOCOL_VERSION 1

/* Op codes (uint16 on wire) */
#define OP_QUERY_AVAIL          0x0001
#define OP_BOOK                 0x0002
#define OP_CHANGE_BOOKING       0x0003
#define OP_MONITOR              0x0004
#define OP_CUSTOM_IDEMPOTENT    0x1001  /* set facility color */
#define OP_CUSTOM_NON_IDEMPOTENT 0x1002 /* increment counter */

/* Error opcode mask */
#define OP_ERROR_MASK           0x8000

/* Error codes (uint16) */
#define ERR_CONFLICT            1
#define ERR_NOT_FOUND           2
#define ERR_BAD_REQUEST         3
#define ERR_INTERNAL            4

/* Flags (uint32 bits) */
#define FLAG_AT_MOST_ONCE       (1U << 0)
#define FLAG_IS_CALLBACK        (1U << 1)

/* Header length in bytes */
#define HEADER_LEN              16

/* Day enumeration for weekly schedule (matches Java) */
typedef enum {
    DAY_MONDAY = 0,
    DAY_TUESDAY = 1,
    DAY_WEDNESDAY = 2,
    DAY_THURSDAY = 3,
    DAY_FRIDAY = 4,
    DAY_SATURDAY = 5,
    DAY_SUNDAY = 6
} Day;

/* WeeklyTime structure - represents time within a week */
typedef struct {
    Day day;           /* day of week (0-6) */
    uint8_t hour;      /* hour (0-23) */
    uint8_t minute;    /* minute (0-59) */
} WeeklyTime;

/*
 * Wire header structure (16 bytes total).
 * IMPORTANT: This struct is NOT directly sent on wire due to padding/alignment.
 * We manually serialize/deserialize each field with proper byte order.
 */
typedef struct {
    uint16_t version;       /* protocol version (=1) */
    uint16_t opCode;        /* operation code */
    uint32_t requestId;     /* unique request identifier */
    uint32_t flags;         /* flags bitmap */
    uint32_t payloadLen;    /* length of payload following header */
} Header;

#endif /* PROTOCOL_H */
