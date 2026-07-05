package com.richwatson.electrofind.util

// Exact h/m display for charge/stay durations, e.g. "45 min", "1h 05min".
// Single shared copy — this was previously duplicated per screen and the
// copies drifted (the old "≥3h" cap bug had to be fixed three times).
fun formatDurationMinutes(mins: Int): String =
    if (mins < 60) "$mins min" else "${mins / 60}h ${"%02d".format(mins % 60)}min"
