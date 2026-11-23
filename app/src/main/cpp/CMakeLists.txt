cmake_minimum_required(VERSION 3.22.1)
project(codeside_pty C)

add_library(codeside-pty SHARED terminal_pty.c)

find_library(log-lib log)

target_link_libraries(codeside-pty ${log-lib})
