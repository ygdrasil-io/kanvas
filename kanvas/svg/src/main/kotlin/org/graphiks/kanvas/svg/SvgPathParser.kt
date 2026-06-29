package org.graphiks.kanvas.svg

import org.graphiks.kanvas.Path

class SvgPathParser {
    fun parse(d: String): Path {
        if (d.isBlank()) return Path()
        
        try {
            val path = Path()
            val commands = d.split(Regex("\\s*(?=[A-Za-z])"))
            var currentX = 0f
            var currentY = 0f
            var startX = 0f
            var startY = 0f

            for (command in commands) {
                if (command.isEmpty()) continue
                val cmd = command[0]
                val args = command.substring(1).trim().split(Regex("[\\s,]+"))
                    .filter { it.isNotEmpty() }
                    .map { it.toFloatOrNull() ?: 0f }

                when (cmd) {
                    'M', 'm' -> {
                        if (args.size >= 2) {
                            val x = if (cmd.isUpperCase()) args[0] else currentX + args[0]
                            val y = if (cmd.isUpperCase()) args[1] else currentY + args[1]
                            path.moveTo(x, y)
                            currentX = x
                            currentY = y
                            startX = x
                            startY = y
                        }
                    }
                    'L', 'l' -> {
                        if (args.size >= 2) {
                            val x = if (cmd.isUpperCase()) args[0] else currentX + args[0]
                            val y = if (cmd.isUpperCase()) args[1] else currentY + args[1]
                            path.lineTo(x, y)
                            currentX = x
                            currentY = y
                        }
                    }
                    'H', 'h' -> {
                        if (args.isNotEmpty()) {
                            val x = if (cmd.isUpperCase()) args[0] else currentX + args[0]
                            path.lineTo(x, currentY)
                            currentX = x
                        }
                    }
                    'V', 'v' -> {
                        if (args.isNotEmpty()) {
                            val y = if (cmd.isUpperCase()) args[0] else currentY + args[0]
                            path.lineTo(currentX, y)
                            currentY = y
                        }
                    }
                    'C', 'c' -> {
                        if (args.size >= 6) {
                            val x1 = if (cmd.isUpperCase()) args[0] else currentX + args[0]
                            val y1 = if (cmd.isUpperCase()) args[1] else currentY + args[1]
                            val x2 = if (cmd.isUpperCase()) args[2] else currentX + args[2]
                            val y2 = if (cmd.isUpperCase()) args[3] else currentY + args[3]
                            val x = if (cmd.isUpperCase()) args[4] else currentX + args[4]
                            val y = if (cmd.isUpperCase()) args[5] else currentY + args[5]
                            path.cubicTo(x1, y1, x2, y2, x, y)
                            currentX = x
                            currentY = y
                        }
                    }
                    'Q', 'q' -> {
                        if (args.size >= 4) {
                            val x1 = if (cmd.isUpperCase()) args[0] else currentX + args[0]
                            val y1 = if (cmd.isUpperCase()) args[1] else currentY + args[1]
                            val x = if (cmd.isUpperCase()) args[2] else currentX + args[2]
                            val y = if (cmd.isUpperCase()) args[3] else currentY + args[3]
                            path.quadTo(x1, y1, x, y)
                            currentX = x
                            currentY = y
                        }
                    }
                    'A', 'a' -> {
                        if (args.size >= 7) {
                            val rx = args[0]
                            val ry = args[1]
                            val xAxisRotation = args[2]
                            val largeArcFlag = args[3].toInt() != 0
                            val sweepFlag = args[4].toInt() != 0
                            val x = if (cmd.isUpperCase()) args[5] else currentX + args[5]
                            val y = if (cmd.isUpperCase()) args[6] else currentY + args[6]
                            // TODO: Implement arcTo when available in Path class
                            // For now, use lineTo as fallback
                            path.lineTo(x, y)
                            currentX = x
                            currentY = y
                        }
                    }
                    'Z', 'z' -> {
                        path.close()
                        currentX = startX
                        currentY = startY
                    }
                }
            }
            return path
        } catch (e: Exception) {
            return Path()
        }
    }
}
