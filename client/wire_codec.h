/*
 * wire_codec.h
 * Purpose: Manual marshalling/unmarshalling functions for UDP protocol in C.
 * Design notes:
 * - All integers are converted to/from network byte order (big-endian).
 * - Strings are length-prefixed: uint16 length + UTF-8 bytes.
 * - Timestamps are int64 (8 bytes) in big-endian.
 * - No struct padding; all fields written byte-by-byte or with htons/htonl.
 */

#ifndef WIRE_CODEC_H
#define WIRE_CODEC_H

#include "protocol.h"
#include <stdint.h>
#include <stddef.h>

/*
 * Write header to buffer at offset 0..15.
 * Returns number of bytes written (always 16).
 */
int write_header(uint8_t *buf, const Header *h);

/*
 * Read header from buffer starting at offset 0.
 * Returns number of bytes read (always 16).
 */
int read_header(const uint8_t *buf, Header *h);

/*
 * Write uint16 in network byte order.
 * Returns number of bytes written (2).
 */
int write_u16(uint8_t *buf, uint16_t val);

/*
 * Write uint32 in network byte order.
 * Returns number of bytes written (4).
 */
int write_u32(uint8_t *buf, uint32_t val);

/*
 * Write int64 in network byte order (big-endian).
 * Returns number of bytes written (8).
 */
int write_i64(uint8_t *buf, int64_t val);

/*
 * Read uint16 from network byte order.
 * Returns number of bytes read (2).
 */
int read_u16(const uint8_t *buf, uint16_t *val);

/*
 * Read uint32 from network byte order.
 * Returns number of bytes read (4).
 */
int read_u32(const uint8_t *buf, uint32_t *val);

/*
 * Read int64 from network byte order.
 * Returns number of bytes read (8).
 */
int read_i64(const uint8_t *buf, int64_t *val);

/*
 * Write length-prefixed string: uint16 length + UTF-8 bytes.
 * Returns total bytes written (2 + strlen).
 */
int write_string(uint8_t *buf, const char *str);

/*
 * Read length-prefixed string into provided buffer (max_len includes null terminator).
 * Returns total bytes read from wire (2 + string length), or -1 on error.
 * Output string is null-terminated.
 */
int read_string(const uint8_t *buf, char *out, size_t max_len);

/*
 * Write WeeklyTime as: uint8 day + uint8 hour + uint8 minute (3 bytes total).
 * Returns number of bytes written (3).
 */
int write_weekly_time(uint8_t *buf, const WeeklyTime *time);

/*
 * Read WeeklyTime from: uint8 day + uint8 hour + uint8 minute (3 bytes total).
 * Returns number of bytes read (3).
 */
int read_weekly_time(const uint8_t *buf, WeeklyTime *time);

/*
 * Helper: convert WeeklyTime to string for display.
 * Returns pointer to static buffer (not thread-safe).
 */


/*
 * Helper: get day name from Day enum.
 * Returns pointer to static string.
 */
const char* day_to_string(Day day);

#endif /* WIRE_CODEC_H */
