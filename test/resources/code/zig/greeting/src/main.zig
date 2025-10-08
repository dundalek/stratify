const std = @import("std");
const greeting = @import("greeting.zig");

pub fn main() !void {
    const stdout = std.fs.File.stdout();
    const message = greeting.greet();
    try stdout.writeAll(message);
    try stdout.writeAll("\n");
}

// zig build run
