# Makefile for C UDP client
# Purpose: Build the C client executable on Windows (MinGW) or Linux/macOS.
# Usage: make (or mingw32-make on Windows with MinGW)

CC = gcc
CFLAGS = -Wall -Wextra -std=c11 -O2
LDFLAGS =

# Windows-specific: link ws2_32 for Winsock if needed (MinGW)
# For POSIX systems (Linux/macOS), no extra libs needed for sockets
ifeq ($(OS),Windows_NT)
    LDFLAGS += -lws2_32
endif

TARGET = client_udp
SRCS = client_main.c wire_codec.c
OBJS = $(SRCS:.c=.o)

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

%.o: %.c protocol.h wire_codec.h
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET) $(TARGET).exe

.PHONY: all clean
