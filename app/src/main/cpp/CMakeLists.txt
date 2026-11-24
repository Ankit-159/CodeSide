cmake_minimum_required(VERSION 3.22.1)
project(codeside_pty C)

add_library(codeside-pty SHARED terminal_pty.c)
add_library(codeside-exec SHARED termux_exec.c)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(codeside-pty ${log-lib})
target_link_libraries(codeside-exec ${log-lib} ${dl-lib})
