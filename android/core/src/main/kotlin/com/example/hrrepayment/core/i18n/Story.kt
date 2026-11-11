package com.example.hrrepayment.core.i18n

import com.example.hrrepayment.core.content.StoryText

/**
 * Language-aware facade over the narrative text.
 *
 * The approved Chinese implementation stays in
 * [com.example.hrrepayment.core.content.StoryText]; the English counterpart lives in
 * [StoryEn] and is generated from the reviewed translation file. Screens call this
 * facade and never reach for either object directly.
 */
object Story {

    fun eventTitle(lang: Lang, id: String): String =
        if (lang == Lang.ZH) StoryText.eventTitle(id) else StoryEn.eventTitle(id)

    fun eventBody(lang: Lang, id: String): String =
        if (lang == Lang.ZH) StoryText.eventBody(id) else StoryEn.eventBody(id)

    fun endingTitle(lang: Lang, id: String?): String =
        if (lang == Lang.ZH) StoryText.endingTitle(id) else StoryEn.endingTitle(id)

    fun endingBody(lang: Lang, id: String?, reason: String?): String =
        if (lang == Lang.ZH) StoryText.endingBody(id, reason) else StoryEn.endingBody(id, reason)
}
