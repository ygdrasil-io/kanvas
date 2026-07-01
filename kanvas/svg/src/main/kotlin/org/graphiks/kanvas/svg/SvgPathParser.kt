package org.graphiks.kanvas.svg

import org.graphiks.kanvas.geometry.Path

class SvgPathParser {
    fun parse(d: String): Path {
        if (d.isBlank()) return Path { }
        
        try {
            val path = Path { }
            val commands = d.split(Regex("\\s*(?=[A-Za-z])"))
            var currentX = 0f
            var currentY = 0f
            var startX = 0f
            var startY = 0f
            var lastControlX = 0f
            var lastControlY = 0f
            var lastWasQuadratic = false

            for (command in commands) {
                if (command.isEmpty()) continue
                val cmd = command[0]
                val args = command.substring(1).trim().split(Regex("[\\s,]+"))
                    .filter { it.isNotEmpty() }
                    .map { it.toFloatOrNull() ?: 0f }

                when (cmd) {
                    'M', 'm' -> {
                        lastWasQuadratic = false
                        var i = 0
                        while (i + 1 < args.size) {
                            val x = if (cmd.isUpperCase()) args[i] else currentX + args[i]
                            val y = if (cmd.isUpperCase()) args[i + 1] else currentY + args[i + 1]
                            if (i == 0) {
                                path.moveTo(x, y)
                                startX = x; startY = y
                            } else {
                                path.lineTo(x, y)
                            }
                            currentX = x; currentY = y
                            lastControlX = x; lastControlY = y
                            i += 2
                        }
                    }
                    'L', 'l' -> {
                        var i = 0
                        while (i + 1 < args.size) {
                            val x = if (cmd.isUpperCase()) args[i] else currentX + args[i]
                            val y = if (cmd.isUpperCase()) args[i + 1] else currentY + args[i + 1]
                            path.lineTo(x, y)
                            currentX = x; currentY = y
                            i += 2
                        }
                    }
                    'H', 'h' -> {
                        for (arg in args) {
                            val x = if (cmd.isUpperCase()) arg else currentX + arg
                            path.lineTo(x, currentY)
                            currentX = x
                        }
                    }
                    'V', 'v' -> {
                        for (arg in args) {
                            val y = if (cmd.isUpperCase()) arg else currentY + arg
                            path.lineTo(currentX, y)
                            currentY = y
                        }
                    }
                    'C', 'c' -> {
                        var i = 0
                        while (i + 5 < args.size) {
                            val x1 = if (cmd.isUpperCase()) args[i] else currentX + args[i]
                            val y1 = if (cmd.isUpperCase()) args[i + 1] else currentY + args[i + 1]
                            val x2 = if (cmd.isUpperCase()) args[i + 2] else currentX + args[i + 2]
                            val y2 = if (cmd.isUpperCase()) args[i + 3] else currentY + args[i + 3]
                            val x = if (cmd.isUpperCase()) args[i + 4] else currentX + args[i + 4]
                            val y = if (cmd.isUpperCase()) args[i + 5] else currentY + args[i + 5]
                            path.cubicTo(x1, y1, x2, y2, x, y)
                            lastControlX = x2; lastControlY = y2
                            lastWasQuadratic = false
                            currentX = x; currentY = y
                            i += 6
                        }
                    }
                    'S', 's' -> {
                        var i = 0
                        while (i + 3 < args.size) {
                            val reflectX = 2 * currentX - lastControlX
                            val reflectY = 2 * currentY - lastControlY
                            val x1 = if (lastWasQuadratic) currentX else reflectX
                            val y1 = if (lastWasQuadratic) currentY else reflectY
                            val x2 = if (cmd.isUpperCase()) args[i] else currentX + args[i]
                            val y2 = if (cmd.isUpperCase()) args[i + 1] else currentY + args[i + 1]
                            val x = if (cmd.isUpperCase()) args[i + 2] else currentX + args[i + 2]
                            val y = if (cmd.isUpperCase()) args[i + 3] else currentY + args[i + 3]
                            path.cubicTo(x1, y1, x2, y2, x, y)
                            lastControlX = x2; lastControlY = y2
                            lastWasQuadratic = false
                            currentX = x; currentY = y
                            i += 4
                        }
                    }
                    'Q', 'q' -> {
                        var i = 0
                        while (i + 3 < args.size) {
                            val x1 = if (cmd.isUpperCase()) args[i] else currentX + args[i]
                            val y1 = if (cmd.isUpperCase()) args[i + 1] else currentY + args[i + 1]
                            val x = if (cmd.isUpperCase()) args[i + 2] else currentX + args[i + 2]
                            val y = if (cmd.isUpperCase()) args[i + 3] else currentY + args[i + 3]
                            path.quadTo(x1, y1, x, y)
                            lastControlX = x1; lastControlY = y1
                            lastWasQuadratic = true
                            currentX = x; currentY = y
                            i += 4
                        }
                    }
                    'T', 't' -> {
                        var i = 0
                        while (i + 1 < args.size) {
                            val reflectX = 2 * currentX - lastControlX
                            val reflectY = 2 * currentY - lastControlY
                            val x1 = if (lastWasQuadratic) reflectX else currentX
                            val y1 = if (lastWasQuadratic) reflectY else currentY
                            val x = if (cmd.isUpperCase()) args[i] else currentX + args[i]
                            val y = if (cmd.isUpperCase()) args[i + 1] else currentY + args[i + 1]
                            path.quadTo(x1, y1, x, y)
                            lastControlX = x1; lastControlY = y1
                            lastWasQuadratic = true
                            currentX = x; currentY = y
                            i += 2
                        }
                    }
                    'A', 'a' -> {
                        var i = 0
                        while (i + 6 < args.size) {
                            val rx = args[i]
                            val ry = args[i + 1]
                            val xAxisRotation = args[i + 2]
                            val largeArcFlag = args[i + 3].toInt() != 0
                            val sweepFlag = args[i + 4].toInt() != 0
                            val x = if (cmd.isUpperCase()) args[i + 5] else currentX + args[i + 5]
                            val y = if (cmd.isUpperCase()) args[i + 6] else currentY + args[i + 6]
                            path.arcTo(rx, ry, xAxisRotation, largeArcFlag, sweepFlag, x, y)
                            currentX = x; currentY = y
                            i += 7
                        }
                    }
                    'Z', 'z' -> {
                        path.close()
                        currentX = startX
                        currentY = startY
                        lastControlX = startX
                        lastControlY = startY
                        lastWasQuadratic = false
                    }
                }
            }
            return path
        } catch (e: Exception) {
            System.err.println("Failed to parse SVG path: ${e.message}")
            return Path { }
        }
    }
}
