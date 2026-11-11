package com.example.hrrepayment.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-authored line icons for the game (24×24, stroke based).
 *
 * They are drawn here rather than pulled from a library for two reasons:
 *  - the specific concepts (debt, luck, dopamine, dossier) have no good stock glyph;
 *  - one shared [ImageVector] per icon is a single immutable object reused by every
 *    call site, so a screen with hundreds of cells allocates nothing extra.
 *
 * Every icon is tinted by `Icon(tint = …)`; the source colour below is irrelevant.
 */
object GameIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.line(block: PathBuilder.() -> Unit): ImageVector.Builder = path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )

    private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit): ImageVector.Builder =
        path(fill = SolidColor(Color.Black), pathBuilder = block)

    /** Circle helper built from four cubic segments (exact bezier approximation). */
    private const val K = 0.5523f

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        val k = K * r
        moveTo(cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        close()
    }

    /** 现金 / Cash — a banknote. */
    val Cash: ImageVector by lazy {
        icon("GameCash") {
            line {
                moveTo(2.5f, 6.5f); lineTo(21.5f, 6.5f); lineTo(21.5f, 17.5f); lineTo(2.5f, 17.5f); close()
            }
            line {
                moveTo(12f, 9.6f); lineTo(14.4f, 12f); lineTo(12f, 14.4f); lineTo(9.6f, 12f); close()
            }
            line {
                moveTo(6f, 10.5f); lineTo(6f, 13.5f)
                moveTo(18f, 10.5f); lineTo(18f, 13.5f)
            }
        }
    }

    /** 负债 / Debt — a broken chain link. */
    val Debt: ImageVector by lazy {
        icon("GameDebt") {
            line {
                moveTo(10.5f, 8f); lineTo(8.5f, 8f)
                quadTo(4f, 8f, 4f, 12f)
                quadTo(4f, 16f, 8.5f, 16f); lineTo(10.5f, 16f)
            }
            line {
                moveTo(13.5f, 8f); lineTo(15.5f, 8f)
                quadTo(20f, 8f, 20f, 12f)
                quadTo(20f, 16f, 15.5f, 16f); lineTo(13.5f, 16f)
            }
        }
    }

    /** 工作能力 / Ability — a lightning bolt. */
    val Ability: ImageVector by lazy {
        icon("GameAbility") {
            solid {
                moveTo(13.2f, 2f); lineTo(5f, 13.6f); lineTo(10.6f, 13.6f)
                lineTo(9.6f, 22f); lineTo(19f, 9.8f); lineTo(12.6f, 9.8f); close()
            }
        }
    }

    /** 信任 / Trust — a shield with a check. */
    val Trust: ImageVector by lazy {
        icon("GameTrust") {
            line {
                moveTo(12f, 2.5f); lineTo(20f, 6f); lineTo(20f, 11.5f)
                quadTo(20f, 17.5f, 12f, 21.5f)
                quadTo(4f, 17.5f, 4f, 11.5f)
                lineTo(4f, 6f); close()
            }
            line { moveTo(8.5f, 11.6f); lineTo(11f, 14.2f); lineTo(15.6f, 9.4f) }
        }
    }

    /** 运气 / Luck — a four-point sparkle. */
    val Luck: ImageVector by lazy {
        icon("GameLuck") {
            line {
                moveTo(12f, 2.5f); lineTo(13.9f, 10.1f); lineTo(21.5f, 12f)
                lineTo(13.9f, 13.9f); lineTo(12f, 21.5f)
                lineTo(10.1f, 13.9f); lineTo(2.5f, 12f); lineTo(10.1f, 10.1f); close()
            }
        }
    }

    /** 健康 / Health — a heart with a pulse line. */
    val Health: ImageVector by lazy {
        icon("GameHealth") {
            line {
                moveTo(12f, 20.5f)
                quadTo(3f, 14f, 3f, 8.8f)
                quadTo(3f, 4.5f, 7f, 4.5f)
                quadTo(10f, 4.5f, 12f, 8f)
                quadTo(14f, 4.5f, 17f, 4.5f)
                quadTo(21f, 4.5f, 21f, 8.8f)
                quadTo(21f, 14f, 12f, 20.5f); close()
            }
            line {
                moveTo(6.2f, 12f); lineTo(9f, 12f); lineTo(10.4f, 9.2f)
                lineTo(12.4f, 15f); lineTo(13.8f, 12f); lineTo(17.8f, 12f)
            }
        }
    }

    /** 多巴胺 / Dopamine — a droplet. */
    val Dopamine: ImageVector by lazy {
        icon("GameDopamine") {
            line {
                moveTo(12f, 3f)
                quadTo(5.5f, 10f, 5.5f, 14f)
                quadTo(5.5f, 19f, 12f, 19f)
                quadTo(18.5f, 19f, 18.5f, 14f)
                quadTo(18.5f, 10f, 12f, 3f); close()
            }
            line { moveTo(9.6f, 14.6f); quadTo(10.2f, 16.8f, 12.4f, 16.8f) }
        }
    }

    /** 英语 / English — a globe. */
    val English: ImageVector by lazy {
        icon("GameEnglish") {
            line { circle(12f, 12f, 9f) }
            line { moveTo(3f, 12f); lineTo(21f, 12f) }
            line {
                moveTo(12f, 3f)
                curveTo(9.4f, 6.2f, 9.4f, 17.8f, 12f, 21f)
                curveTo(14.6f, 17.8f, 14.6f, 6.2f, 12f, 3f); close()
            }
        }
    }

    /** 面试档案袋 / Dossier — a document folder. */
    val Dossier: ImageVector by lazy {
        icon("GameDossier") {
            line {
                moveTo(3f, 18.5f); lineTo(3f, 5.5f); lineTo(9.4f, 5.5f)
                lineTo(11.4f, 8.6f); lineTo(21f, 8.6f); lineTo(21f, 18.5f); close()
            }
            line { moveTo(3f, 12.2f); lineTo(21f, 12.2f) }
        }
    }

    /** 文件夹 / Folder — the classic tabbed folder (used by the watermark). */
    val Folder: ImageVector by lazy {
        icon("GameFolder") {
            line {
                moveTo(3f, 18.8f); lineTo(3f, 6.2f); lineTo(9.2f, 6.2f)
                lineTo(11.2f, 9.2f); lineTo(21f, 9.2f); lineTo(21f, 18.8f); close()
            }
            line { moveTo(3f, 12.6f); lineTo(21f, 12.6f) }
        }
    }

    /** 钱袋 / Money bag — a tied sack with a ¥ mark. */
    val MoneyBag: ImageVector by lazy {
        icon("GameMoneyBag") {
            line {
                moveTo(9.2f, 6.2f)
                quadTo(4.4f, 9.4f, 4.4f, 14.2f)
                quadTo(4.4f, 20.2f, 12f, 20.2f)
                quadTo(19.6f, 20.2f, 19.6f, 14.2f)
                quadTo(19.6f, 9.4f, 14.8f, 6.2f); close()
            }
            line {
                moveTo(8.6f, 6.2f); lineTo(15.4f, 6.2f)
                moveTo(10.2f, 3.4f); lineTo(12f, 6.2f); lineTo(13.8f, 3.4f)
            }
            line {
                moveTo(9.8f, 11.2f); lineTo(12f, 13.6f); lineTo(14.2f, 11.2f)
                moveTo(12f, 13.6f); lineTo(12f, 17.4f)
                moveTo(9.9f, 13.9f); lineTo(14.1f, 13.9f)
                moveTo(9.9f, 15.6f); lineTo(14.1f, 15.6f)
            }
        }
    }

    /** 新闻 / News — a newspaper. */
    val News: ImageVector by lazy {
        icon("GameNews") {
            line {
                moveTo(3f, 5f); lineTo(17.5f, 5f); lineTo(17.5f, 19f); lineTo(3f, 19f); close()
            }
            line {
                moveTo(17.5f, 8.5f); lineTo(20.5f, 8.5f); lineTo(20.5f, 17f)
                quadTo(20.5f, 19f, 18.5f, 19f)
            }
            line {
                moveTo(5.5f, 8.5f); lineTo(15f, 8.5f)
                moveTo(5.5f, 11.8f); lineTo(15f, 11.8f)
                moveTo(5.5f, 15.1f); lineTo(11f, 15.1f)
            }
        }
    }

    /** 状态 / Status — a bar chart. */
    val Status: ImageVector by lazy {
        icon("GameStatus") {
            line { moveTo(4f, 20f); lineTo(20f, 20f) }
            line {
                moveTo(7.2f, 20f); lineTo(7.2f, 12f)
                moveTo(12f, 20f); lineTo(12f, 6.5f)
                moveTo(16.8f, 20f); lineTo(16.8f, 15f)
            }
        }
    }

    /** 日历 / Calendar. */
    val Calendar: ImageVector by lazy {
        icon("GameCalendar") {
            line {
                moveTo(3.5f, 6f); lineTo(20.5f, 6f); lineTo(20.5f, 20f); lineTo(3.5f, 20f); close()
            }
            line { moveTo(3.5f, 10.4f); lineTo(20.5f, 10.4f) }
            line { moveTo(8f, 3.5f); lineTo(8f, 7.5f); moveTo(16f, 3.5f); lineTo(16f, 7.5f) }
        }
    }


    /** 设置 / Settings — 滑杆式图标（三条横线 + 圆钮），小尺寸下比齿轮更清楚。 */
    val Settings: ImageVector by lazy {
        icon("GameSettings") {
            line { moveTo(4f, 7f); lineTo(20f, 7f) }
            line { moveTo(4f, 12f); lineTo(20f, 12f) }
            line { moveTo(4f, 17f); lineTo(20f, 17f) }
            // 三个圆钮：用短竖线表示“可拖动”
            line { moveTo(9f, 4.6f); lineTo(9f, 9.4f) }
            line { moveTo(15f, 9.6f); lineTo(15f, 14.4f) }
            line { moveTo(9f, 14.6f); lineTo(9f, 19.4f) }
        }
    }

    /** 推送 / Push — a paper plane. */
    val Push: ImageVector by lazy {
        icon("GamePush") {
            line {
                moveTo(3f, 11.2f); lineTo(21f, 3.5f); lineTo(13.4f, 20.5f)
                lineTo(10.9f, 13.2f); close()
            }
            line { moveTo(10.9f, 13.2f); lineTo(21f, 3.5f) }
        }
    }

    /** 竞猜 / Bet — a ticket. */
    val Bet: ImageVector by lazy {
        icon("GameBet") {
            line {
                moveTo(3f, 8f); lineTo(21f, 8f); lineTo(21f, 16f); lineTo(3f, 16f); close()
            }
            line { moveTo(15.2f, 8f); lineTo(15.2f, 16f) }
            line { moveTo(7f, 10.8f); lineTo(7f, 13.2f) }
        }
    }

    /** 成就 / Trophy. */
    val Trophy: ImageVector by lazy {
        icon("GameTrophy") {
            line {
                moveTo(9f, 4f); lineTo(9f, 11f)
                quadTo(9f, 15f, 12f, 15f)
                quadTo(15f, 15f, 15f, 11f)
                lineTo(15f, 4f); close()
            }
            line {
                moveTo(9f, 5.5f); lineTo(5.6f, 5.5f); lineTo(5.6f, 8f)
                quadTo(5.6f, 11f, 9f, 11f)
            }
            line {
                moveTo(15f, 5.5f); lineTo(18.4f, 5.5f); lineTo(18.4f, 8f)
                quadTo(18.4f, 11f, 15f, 11f)
            }
            line {
                moveTo(12f, 15f); lineTo(12f, 18f)
                moveTo(8f, 20.5f); lineTo(16f, 20.5f)
                moveTo(9.6f, 18f); lineTo(14.4f, 18f)
            }
        }
    }

    /** 警告 / Warning — a triangle with an exclamation mark. */
    val Warning: ImageVector by lazy {
        icon("GameWarning") {
            line { moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close() }
            line { moveTo(12f, 9.6f); lineTo(12f, 14.6f) }
            solid { moveTo(11.1f, 16.6f); lineTo(12.9f, 16.6f); lineTo(12.9f, 18.4f); lineTo(11.1f, 18.4f); close() }
        }
    }

    /** 已勾选 / Check. */
    val Check: ImageVector by lazy {
        icon("GameCheck") {
            line { moveTo(4.5f, 12.6f); lineTo(9.6f, 17.6f); lineTo(19.5f, 6.4f) }
        }
    }

    /** 未解锁 / Locked. */
    val Locked: ImageVector by lazy {
        icon("GameLocked") {
            line { moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 20.5f); lineTo(5f, 20.5f); close() }
            line {
                moveTo(8f, 11f); lineTo(8f, 7.6f)
                curveTo(8f, 4.6f, 16f, 4.6f, 16f, 7.6f)
                lineTo(16f, 11f)
            }
        }
    }

    /** 缩放提示 / Pinch zoom. */
    val Zoom: ImageVector by lazy {
        icon("GameZoom") {
            line { circle(10.4f, 10.4f, 6.4f) }
            line { moveTo(15.2f, 15.2f); lineTo(21f, 21f) }
            line {
                moveTo(10.4f, 7.4f); lineTo(10.4f, 13.4f)
                moveTo(7.4f, 10.4f); lineTo(13.4f, 10.4f)
            }
        }
    }

    /** 人物 / Person — the dossier entry mark. */
    val Person: ImageVector by lazy {
        icon("GamePerson") {
            line { circle(9.4f, 8f, 3.3f) }
            line {
                moveTo(3.2f, 19.8f)
                quadTo(3.2f, 13.6f, 9.4f, 13.6f)
                quadTo(15.6f, 13.6f, 15.6f, 19.8f)
            }
            line {
                moveTo(16.4f, 5.2f)
                quadTo(20.2f, 6.2f, 20.2f, 9.6f)
                quadTo(20.2f, 12.4f, 17.6f, 13.2f)
            }
            line {
                moveTo(18.2f, 14.4f)
                quadTo(21.2f, 15.6f, 21.2f, 19.8f)
            }
        }
    }
}
