package com.mytvb.feature.series

import android.content.Context
import com.mytvb.R
import com.mytvb.model.series.AllSeriesFilterModel
import com.mytvb.model.series.AllSeriesFilterOption
import com.mytvb.model.series.SeriesType

object AllSeriesFilterFactory {

    fun create(context: Context, seasonType: Int): List<AllSeriesFilterModel> {
        return when (seasonType) {
            SeriesType.ANIME -> createAnimeFilters(context)
            SeriesType.CHINA_ANIME -> createChinaAnimeFilters(context)
            SeriesType.MOVIE -> createMovieFilters(context)
            SeriesType.DRAMA -> createDramaFilters(context)
            SeriesType.DOCUMENTARY -> createDocumentaryFilters(context)
            SeriesType.VARIETY -> createVarietyFilters(context)
            else -> createAnimeFilters(context)
        }
    }

    fun createFiltersForUrl(context: Context, url: String, seasonType: Int): List<AllSeriesFilterModel> {
        val urlParams = parseUrlParams(url)
        val filters = when (seasonType) {
            SeriesType.ANIME -> createAnimeFiltersNoContext(context)
            SeriesType.CHINA_ANIME -> createChinaAnimeFiltersNoContext(context)
            SeriesType.MOVIE -> createMovieFiltersNoContext(context)
            SeriesType.DRAMA -> createDramaFiltersNoContext(context)
            SeriesType.DOCUMENTARY -> createDocumentaryFiltersNoContext(context)
            SeriesType.VARIETY -> createVarietyFiltersNoContext(context)
            else -> createAnimeFiltersNoContext(context)
        }
        return applyUrlParamsToFilters(filters, urlParams)
    }

    fun applyInitialFilters(
        context: Context,
        seasonType: Int,
        moreUrl: String
    ): List<AllSeriesFilterModel> {
        if (moreUrl.isBlank()) {
            return create(context, seasonType)
        }
        val urlFilters = createFiltersForUrl(context, moreUrl, seasonType)
        val defaultFilters = create(context, seasonType)
        return defaultFilters.map { defaultFilter ->
            if (shouldIgnoreInitialUrlSelection(seasonType, defaultFilter.key)) {
                return@map defaultFilter
            }
            val urlFilter = urlFilters.find { it.key == defaultFilter.key }
            if (urlFilter != null && urlFilter.currentSelect > 0) {
                val matchedIndex = defaultFilter.options.indexOfFirst { it.value == urlFilter.options[urlFilter.currentSelect].value }
                if (matchedIndex >= 0) {
                    defaultFilter.copy(currentSelect = matchedIndex)
                } else {
                    defaultFilter
                }
            } else {
                defaultFilter
            }
        }
    }

    private fun shouldIgnoreInitialUrlSelection(seasonType: Int, filterKey: String): Boolean {
        return filterKey == "order" || (seasonType == SeriesType.ANIME && filterKey == "area")
    }

    private fun createAnimeFilters(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilter(context, SeriesType.ANIME))
        add(createVersionTypeFilter(context))
        add(createSpokenLanguageFilter(context))
        add(createAreaFilter(context, SeriesType.ANIME))
        add(createStatusFilter(context))
        add(createPayTypeFilter(context))
        add(createSeasonMonthFilter(context))
        add(createAnimeYearFilter(context))
        add(createStyleFilter(context, SeriesType.ANIME))
    }

    private fun createAnimeFiltersNoContext(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilterNoContext(context, SeriesType.ANIME))
        add(createVersionTypeFilterNoContext(context))
        add(createSpokenLanguageFilterNoContext(context))
        add(createAreaFilterNoContext(context, SeriesType.ANIME))
        add(createStatusFilterNoContext(context))
        add(createPayTypeFilterNoContext(context))
        add(createSeasonMonthFilterNoContext(context))
        add(createAnimeYearFilterNoContext(context))
        add(createStyleFilterNoContext(context, SeriesType.ANIME))
    }

    private fun createChinaAnimeFilters(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilter(context, SeriesType.CHINA_ANIME))
        add(createVersionTypeFilter(context))
        add(createStatusFilter(context))
        add(createPayTypeFilter(context))
        add(createAnimeYearFilter(context))
        add(createStyleFilter(context, SeriesType.CHINA_ANIME))
    }

    private fun createChinaAnimeFiltersNoContext(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilterNoContext(context, SeriesType.CHINA_ANIME))
        add(createVersionTypeFilterNoContext(context))
        add(createStatusFilterNoContext(context))
        add(createPayTypeFilterNoContext(context))
        add(createAnimeYearFilterNoContext(context))
        add(createStyleFilterNoContext(context, SeriesType.CHINA_ANIME))
    }

    private fun createMovieFilters(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilter(context, SeriesType.MOVIE))
        add(createAreaFilter(context, SeriesType.MOVIE))
        add(createStyleFilter(context, SeriesType.MOVIE))
        add(createReleaseDateFilter(context))
        add(createPayTypeFilter(context))
    }

    private fun createMovieFiltersNoContext(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilterNoContext(context, SeriesType.MOVIE))
        add(createAreaFilterNoContext(context, SeriesType.MOVIE))
        add(createStyleFilterNoContext(context, SeriesType.MOVIE))
        add(createReleaseDateFilterNoContext(context))
        add(createPayTypeFilterNoContext(context))
    }

    private fun createDramaFilters(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilter(context, SeriesType.DRAMA))
        add(createAreaFilter(context, SeriesType.DRAMA))
        add(createStyleFilter(context, SeriesType.DRAMA))
        add(createReleaseDateFilter(context))
        add(createPayTypeFilter(context))
    }

    private fun createDramaFiltersNoContext(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilterNoContext(context, SeriesType.DRAMA))
        add(createAreaFilterNoContext(context, SeriesType.DRAMA))
        add(createStyleFilterNoContext(context, SeriesType.DRAMA))
        add(createReleaseDateFilterNoContext(context))
        add(createPayTypeFilterNoContext(context))
    }

    private fun createDocumentaryFilters(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilter(context, SeriesType.DOCUMENTARY))
        add(createStyleFilter(context, SeriesType.DOCUMENTARY))
        add(createProducerFilter(context))
        add(createReleaseDateFilter(context))
        add(createPayTypeFilter(context))
    }

    private fun createDocumentaryFiltersNoContext(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilterNoContext(context, SeriesType.DOCUMENTARY))
        add(createStyleFilterNoContext(context, SeriesType.DOCUMENTARY))
        add(createProducerFilterNoContext(context))
        add(createReleaseDateFilterNoContext(context))
        add(createPayTypeFilterNoContext(context))
    }

    private fun createVarietyFilters(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilter(context, SeriesType.VARIETY))
        add(createPayTypeFilter(context))
        add(createStyleFilter(context, SeriesType.VARIETY))
    }

    private fun createVarietyFiltersNoContext(context: Context): List<AllSeriesFilterModel> = buildList {
        add(createOrderFilterNoContext(context, SeriesType.VARIETY))
        add(createPayTypeFilterNoContext(context))
        add(createStyleFilterNoContext(context, SeriesType.VARIETY))
    }

    private fun parseUrlParams(url: String): Map<String, String> {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return emptyMap()
        val query = url.substring(queryStart + 1)
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    private fun applyUrlParamsToFilters(
        filters: List<AllSeriesFilterModel>,
        urlParams: Map<String, String>
    ): List<AllSeriesFilterModel> {
        return filters.map { filter ->
            val paramValue = urlParams[filter.key] ?: return@map filter
            if (paramValue == "-1" || paramValue.isBlank()) return@map filter
            val matchedIndex = filter.options.indexOfFirst { it.value == paramValue }
            if (matchedIndex >= 0) {
                filter.copy(currentSelect = matchedIndex)
            } else {
                filter
            }
        }
    }

    private fun createAreaFilterNoContext(context: Context, seasonType: Int): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_area),
            key = "area",
            iconResourceId = 0,
            options = createAreaOptions(context, seasonType)
        )
    }

    private fun createStyleFilterNoContext(context: Context, seasonType: Int): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_style),
            key = "style_id",
            iconResourceId = 0,
            options = createStyleOptions(context, seasonType)
        )
    }

    private fun createOrderFilterNoContext(context: Context, seasonType: Int): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_order),
            key = "order",
            iconResourceId = 0,
            options = createOrderOptions(context, seasonType)
        )
    }

    private fun createStatusFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_status),
            key = "is_finish",
            iconResourceId = 0,
            options = createStatusOptions(context)
        )
    }

    private fun createPayTypeFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_pay_type),
            key = "season_status",
            iconResourceId = 0,
            options = createPayTypeOptions(context)
        )
    }

    private fun createReleaseDateFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_year),
            key = "release_date",
            iconResourceId = 0,
            options = createReleaseDateOptions(context)
        )
    }

    private fun createAnimeYearFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_year),
            key = "year",
            iconResourceId = 0,
            options = createAnimeYearOptions(context)
        )
    }

    private fun createVersionTypeFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_type),
            key = "season_version",
            iconResourceId = 0,
            options = createVersionTypeOptions(context)
        )
    }

    private fun createSpokenLanguageFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_spoken),
            key = "spoken_language_type",
            iconResourceId = 0,
            options = createSpokenLanguageOptions(context)
        )
    }

    private fun createSeasonMonthFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_season),
            key = "season_month",
            iconResourceId = 0,
            options = createSeasonMonthOptions(context)
        )
    }

    private fun createProducerFilterNoContext(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_publish),
            key = "producer_id",
            iconResourceId = 0,
            options = createProducerOptions(context)
        )
    }

    private fun createAreaFilter(context: Context, seasonType: Int): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_area),
            key = "area",
            iconResourceId = R.drawable.ic_earth,
            options = createAreaOptions(context, seasonType)
        )
    }

    private fun createStyleFilter(context: Context, seasonType: Int): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_style),
            key = "style_id",
            iconResourceId = R.drawable.tab_dynamic,
            options = createStyleOptions(context, seasonType)
        )
    }

    private fun createOrderFilter(context: Context, seasonType: Int): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_order),
            key = "order",
            iconResourceId = R.drawable.ic_sort,
            options = createOrderOptions(context, seasonType)
        )
    }

    private fun createStatusFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_status),
            key = "is_finish",
            iconResourceId = R.drawable.ic_status,
            options = createStatusOptions(context)
        )
    }

    private fun createPayTypeFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_pay_type),
            key = "season_status",
            iconResourceId = R.drawable.ic_pay,
            options = createPayTypeOptions(context)
        )
    }

    private fun createReleaseDateFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_year),
            key = "release_date",
            iconResourceId = R.drawable.ic_calendar,
            options = createReleaseDateOptions(context)
        )
    }

    private fun createAnimeYearFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_year),
            key = "year",
            iconResourceId = R.drawable.ic_calendar,
            options = createAnimeYearOptions(context)
        )
    }

    private fun createVersionTypeFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_type),
            key = "season_version",
            iconResourceId = R.drawable.tab_recommend,
            options = createVersionTypeOptions(context)
        )
    }

    private fun createSpokenLanguageFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_spoken),
            key = "spoken_language_type",
            iconResourceId = R.drawable.ic_voice,
            options = createSpokenLanguageOptions(context)
        )
    }

    private fun createSeasonMonthFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_season),
            key = "season_month",
            iconResourceId = R.drawable.ic_flower,
            options = createSeasonMonthOptions(context)
        )
    }

    private fun createProducerFilter(context: Context): AllSeriesFilterModel {
        return AllSeriesFilterModel(
            title = context.getString(R.string.filter_publish),
            key = "producer_id",
            iconResourceId = R.drawable.ic_tv,
            options = createProducerOptions(context)
        )
    }

    private fun createAreaOptions(context: Context, seasonType: Int): List<AllSeriesFilterOption> {
        fun areaTitle(resId: Int): String = context.getString(resId)
        return when (seasonType) {
            SeriesType.ANIME -> listOf(
                option(areaTitle(R.string.series_filter_area_all), "-1"),
                option(areaTitle(R.string.series_filter_area_japan), "2"),
                option(areaTitle(R.string.series_filter_area_usa), "3"),
                option(areaTitle(R.string.series_filter_area_other), "1,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70")
            )
            SeriesType.DRAMA -> listOf(
                option(areaTitle(R.string.series_filter_area_all), "-1"),
                option(areaTitle(R.string.series_filter_area_china), "1"),
                option(areaTitle(R.string.series_filter_area_japan), "2"),
                option(areaTitle(R.string.series_filter_area_usa), "3"),
                option(areaTitle(R.string.series_filter_area_uk), "4"),
                option(areaTitle(R.string.series_filter_area_thailand), "10"),
                option(areaTitle(R.string.series_filter_area_other), "5,8,9,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70")
            )
            else -> listOf(
                option(areaTitle(R.string.series_filter_area_all), "-1"),
                option(areaTitle(R.string.series_filter_area_china_mainland), "1"),
                option(areaTitle(R.string.series_filter_area_china_hk_tw), "6,7"),
                option(areaTitle(R.string.series_filter_area_usa), "3"),
                option(areaTitle(R.string.series_filter_area_japan), "2"),
                option(areaTitle(R.string.series_filter_area_korea), "8"),
                option(areaTitle(R.string.series_filter_area_france), "9"),
                option(areaTitle(R.string.series_filter_area_uk), "4"),
                option(areaTitle(R.string.series_filter_area_germany), "15"),
                option(areaTitle(R.string.series_filter_area_thailand), "10"),
                option(areaTitle(R.string.series_filter_area_italy), "35"),
                option(areaTitle(R.string.series_filter_area_spain), "13"),
                option(areaTitle(R.string.series_filter_area_other), "5,11,12,14,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70")
            )
        }
    }

    private fun createStyleOptions(context: Context, seasonType: Int): List<AllSeriesFilterOption> {
        fun t(resId: Int): String = context.getString(resId)
        return when (seasonType) {
            SeriesType.ANIME, SeriesType.CHINA_ANIME -> listOf(
                option(t(R.string.series_style_all), "-1"),
                option(t(R.string.series_style_original), "10010"),
                option(t(R.string.series_style_manga_adapted), "10011"),
                option(t(R.string.series_style_novel_adapted), "10012"),
                option(t(R.string.series_style_game_adapted), "10013"),
                option(t(R.string.series_style_tokusatsu), "10102"),
                option(t(R.string.series_style_puppet_show), "10015"),
                option(t(R.string.series_style_hot_blooded), "10016"),
                option(t(R.string.series_style_time_travel), "10017"),
                option(t(R.string.series_style_fantasy), "10018"),
                option(t(R.string.series_style_battle), "10020"),
                option(t(R.string.series_style_funny), "10021"),
                option(t(R.string.series_style_daily_life), "10022"),
                option(t(R.string.series_style_sci_fi), "10023"),
                option(t(R.string.series_style_moe), "10024"),
                option(t(R.string.series_style_healing), "10025"),
                option(t(R.string.series_style_school), "10026"),
                option(t(R.string.series_style_children), "10027"),
                option(t(R.string.series_style_instant_noodle), "10028"),
                option(t(R.string.series_style_romance), "10029"),
                option(t(R.string.series_style_shoujo), "10030"),
                option(t(R.string.series_style_magic), "10031"),
                option(t(R.string.series_style_adventure), "10032"),
                option(t(R.string.series_style_history), "10033"),
                option(t(R.string.series_style_alternate_world), "10034"),
                option(t(R.string.series_style_mecha), "10035"),
                option(t(R.string.series_style_gods_and_demons), "10036"),
                option(t(R.string.series_style_voice), "10037"),
                option(t(R.string.series_style_sports), "10038"),
                option(t(R.string.series_style_inspirational), "10039"),
                option(t(R.string.series_style_music), "10040"),
                option(t(R.string.series_style_mystery), "10041"),
                option(t(R.string.series_style_club), "10042"),
                option(t(R.string.series_style_battle_of_wits), "10043"),
                option(t(R.string.series_style_tearjerker), "10044"),
                option(t(R.string.series_style_food), "10045"),
                option(t(R.string.series_style_idol), "10046"),
                option(t(R.string.series_style_otome), "10047"),
                option(t(R.string.series_style_workplace), "10048")
            )
            SeriesType.DRAMA -> listOf(
                option(t(R.string.series_style_all), "-1"),
                option(t(R.string.series_style_funny), "10021"),
                option(t(R.string.series_style_fantasy), "10018"),
                option(t(R.string.series_style_war), "10058"),
                option(t(R.string.series_style_wuxia), "10078"),
                option(t(R.string.series_style_youth), "10079"),
                option(t(R.string.series_style_short_drama), "10103"),
                option(t(R.string.series_style_urban), "10080"),
                option(t(R.string.series_style_costume_drama), "10081"),
                option(t(R.string.series_style_spy_war), "10082"),
                option(t(R.string.series_style_classic), "10083"),
                option(t(R.string.series_style_emotion), "10084"),
                option(t(R.string.series_style_suspense), "10057"),
                option(t(R.string.series_style_inspirational), "10039"),
                option(t(R.string.series_style_mythology), "10085"),
                option(t(R.string.series_style_time_travel), "10017"),
                option(t(R.string.series_style_era), "10086"),
                option(t(R.string.series_style_rural), "10087"),
                option(t(R.string.series_style_criminal_investigation), "10088"),
                option(t(R.string.series_style_drama), "10050"),
                option(t(R.string.series_style_family), "10061"),
                option(t(R.string.series_style_history), "10033"),
                option(t(R.string.series_style_military_life), "10089")
            )
            SeriesType.MOVIE -> listOf(
                option(t(R.string.series_style_all), "-1"),
                option(t(R.string.series_style_short_film), "10104"),
                option(t(R.string.series_style_drama), "10050"),
                option(t(R.string.series_style_comedy), "10051"),
                option(t(R.string.series_style_love), "10052"),
                option(t(R.string.series_style_action), "10053"),
                option(t(R.string.series_style_horror), "10054"),
                option(t(R.string.series_style_sci_fi), "10023"),
                option(t(R.string.series_style_crime), "10055"),
                option(t(R.string.series_style_thriller), "10056"),
                option(t(R.string.series_style_suspense), "10057"),
                option(t(R.string.series_style_fantasy), "10018"),
                option(t(R.string.series_style_war), "10058"),
                option(t(R.string.series_style_animation), "10059"),
                option(t(R.string.series_style_biography), "10060"),
                option(t(R.string.series_style_family), "10061"),
                option(t(R.string.series_style_musical), "10062"),
                option(t(R.string.series_style_history), "10033"),
                option(t(R.string.series_style_adventure), "10032"),
                option(t(R.string.series_style_documentary), "10063"),
                option(t(R.string.series_style_disaster), "10064"),
                option(t(R.string.series_style_manga_adapted), "10011"),
                option(t(R.string.series_style_novel_adapted), "10012")
            )
            SeriesType.VARIETY -> listOf(
                option(t(R.string.series_style_all), "-1"),
                option(t(R.string.series_style_music), "10040"),
                option(t(R.string.series_style_interview), "10090"),
                option(t(R.string.series_style_talk_show), "10091"),
                option(t(R.string.series_style_reality_show), "10092"),
                option(t(R.string.series_style_talent_show), "10094"),
                option(t(R.string.series_style_food), "10045"),
                option(t(R.string.series_style_travel), "10095"),
                option(t(R.string.series_style_gala), "10098"),
                option(t(R.string.series_style_concert), "10096"),
                option(t(R.string.series_style_emotion), "10084"),
                option(t(R.string.series_style_comedy), "10051"),
                option(t(R.string.series_style_parent_child), "10097"),
                option(t(R.string.series_style_culture), "10100"),
                option(t(R.string.series_style_workplace), "10048"),
                option(t(R.string.series_style_cute_pets), "10069"),
                option(t(R.string.series_style_nurture), "10099")
            )
            else -> listOf(
                option(t(R.string.series_style_all), "-1"),
                option(t(R.string.series_style_history), "10033"),
                option(t(R.string.series_style_food), "10045"),
                option(t(R.string.series_style_humanities), "10065"),
                option(t(R.string.series_style_technology), "10066"),
                option(t(R.string.series_style_exploration), "10067"),
                option(t(R.string.series_style_space), "10068"),
                option(t(R.string.series_style_cute_pets), "10069"),
                option(t(R.string.series_style_society), "10070"),
                option(t(R.string.series_style_animal), "10071"),
                option(t(R.string.series_style_nature), "10072"),
                option(t(R.string.series_style_medical), "10073"),
                option(t(R.string.series_style_military), "10074"),
                option(t(R.string.series_style_disaster), "10064"),
                option(t(R.string.series_style_crime_case), "10075"),
                option(t(R.string.series_style_mysterious), "10076"),
                option(t(R.string.series_style_trip), "10077"),
                option(t(R.string.series_style_sports), "10038"),
                option(t(R.string.series_style_movie), "-10")
            )
        }
    }

    private fun createOrderOptions(context: Context, seasonType: Int): List<AllSeriesFilterOption> {
        fun t(resId: Int): String = context.getString(resId)
        return when (seasonType) {
            SeriesType.ANIME, SeriesType.CHINA_ANIME -> listOf(
                option(t(R.string.series_order_follow_count), "3"),
                option(t(R.string.series_order_recent_update), "0"),
                option(t(R.string.series_order_highest_rating), "4"),
                option(t(R.string.series_order_play_count), "2"),
                option(t(R.string.series_order_air_date), "5")
            )
            SeriesType.VARIETY -> listOf(
                option(t(R.string.series_order_play_count), "2"),
                option(t(R.string.series_order_recent_update), "0"),
                option(t(R.string.series_order_newest_release), "6"),
                option(t(R.string.series_order_highest_rating), "4"),
                option(t(R.string.series_order_danmaku_count), "1")
            )
            SeriesType.MOVIE -> listOf(
                option(t(R.string.series_order_play_count), "2"),
                option(t(R.string.series_order_recent_update), "0"),
                option(t(R.string.series_order_newest_release), "6"),
                option(t(R.string.series_order_highest_rating), "4")
            )
            SeriesType.DRAMA -> listOf(
                option(t(R.string.series_order_play_count), "2"),
                option(t(R.string.series_order_recent_update), "0"),
                option(t(R.string.series_order_danmaku_count), "1"),
                option(t(R.string.series_order_drama_follow_count), "3"),
                option(t(R.string.series_order_highest_rating), "4")
            )
            SeriesType.DOCUMENTARY -> listOf(
                option(t(R.string.series_order_play_count), "2"),
                option(t(R.string.series_order_highest_rating), "4"),
                option(t(R.string.series_order_recent_update), "0"),
                option(t(R.string.series_order_newest_release), "6"),
                option(t(R.string.series_order_danmaku_count), "1")
            )
            else -> listOf(option(t(R.string.series_order_default), "-1"))
        }
    }

    private fun createStatusOptions(context: Context): List<AllSeriesFilterOption> {
        return listOf(
            option(context.getString(R.string.series_status_all), "-1"),
            option(context.getString(R.string.series_status_finished), "1"),
            option(context.getString(R.string.series_status_ongoing), "0")
        )
    }

    private fun createPayTypeOptions(context: Context): List<AllSeriesFilterOption> {
        return listOf(
            option(context.getString(R.string.series_pay_all), "-1"),
            option(context.getString(R.string.series_pay_free), "1"),
            option(context.getString(R.string.series_pay_paid), "2,6"),
            option(context.getString(R.string.series_pay_vip), "4,6")
        )
    }

    private fun createVersionTypeOptions(context: Context): List<AllSeriesFilterOption> {
        return listOf(
            option(context.getString(R.string.series_filter_version_all), "-1"),
            option(context.getString(R.string.series_filter_version_main), "1"),
            option(context.getString(R.string.series_style_movie), "2"),
            option(context.getString(R.string.series_filter_version_other), "3")
        )
    }

    private fun createSpokenLanguageOptions(context: Context): List<AllSeriesFilterOption> {
        return listOf(
            option(context.getString(R.string.series_filter_spoken_all), "-1"),
            option(context.getString(R.string.series_filter_spoken_original), "1"),
            option(context.getString(R.string.series_filter_spoken_chinese), "2")
        )
    }

    private fun createSeasonMonthOptions(context: Context): List<AllSeriesFilterOption> {
        fun month(month: Int): String = context.getString(R.string.series_filter_month_format, month)
        return listOf(
            option(context.getString(R.string.series_filter_season_all), "-1"),
            option(month(1), "1"),
            option(month(4), "4"),
            option(month(7), "7"),
            option(month(10), "10")
        )
    }

    private fun createProducerOptions(context: Context): List<AllSeriesFilterOption> {
        return listOf(
            option(context.getString(R.string.series_filter_producer_all), "-1"),
            option("CCTV", "1"),
            option("BBC", "2"),
            option("Discovery", "3"),
            option(context.getString(R.string.series_filter_producer_national_geographic), "4"),
            option("NHK", "5"),
            option(context.getString(R.string.series_filter_producer_history_channel), "7"),
            option(context.getString(R.string.series_filter_producer_satellite_tv), "8"),
            option(context.getString(R.string.series_filter_producer_self_produced), "9"),
            option("ITV", "10"),
            option("SKY", "11"),
            option("ZDF", "12"),
            option(context.getString(R.string.series_filter_producer_partner), "13"),
            option(context.getString(R.string.series_filter_producer_other_domestic), "14"),
            option(context.getString(R.string.series_filter_producer_other_overseas), "15")
        )
    }

    private fun createReleaseDateOptions(context: Context): List<AllSeriesFilterOption> {
        fun decade(year: Int): String = context.getString(R.string.series_filter_year_decade_format, year)
        return listOf(
            option(context.getString(R.string.series_filter_year_all), "-1"),
            option("2026", "[2026-01-01 00:00:00,2027-01-01 00:00:00)"),
            option("2025", "[2025-01-01 00:00:00,2026-01-01 00:00:00)"),
            option("2024", "[2024-01-01 00:00:00,2025-01-01 00:00:00)"),
            option("2023", "[2023-01-01 00:00:00,2024-01-01 00:00:00)"),
            option("2022", "[2022-01-01 00:00:00,2023-01-01 00:00:00)"),
            option("2021", "[2021-01-01 00:00:00,2022-01-01 00:00:00)"),
            option("2020", "[2020-01-01 00:00:00,2021-01-01 00:00:00)"),
            option("2019", "[2019-01-01 00:00:00,2020-01-01 00:00:00)"),
            option("2018", "[2018-01-01 00:00:00,2019-01-01 00:00:00)"),
            option("2017", "[2017-01-01 00:00:00,2018-01-01 00:00:00)"),
            option("2016", "[2016-01-01 00:00:00,2017-01-01 00:00:00)"),
            option("2015-2010", "[2010-01-01 00:00:00,2016-01-01 00:00:00)"),
            option("2009-2005", "[2005-01-01 00:00:00,2010-01-01 00:00:00)"),
            option("2004-2000", "[2000-01-01 00:00:00,2005-01-01 00:00:00)"),
            option(decade(90), "[1990-01-01 00:00:00,2000-01-01 00:00:00)"),
            option(decade(80), "[1980-01-01 00:00:00,1990-01-01 00:00:00)"),
            option(context.getString(R.string.series_filter_year_earlier), "[,1980-01-01 00:00:00)")
        )
    }

    private fun createAnimeYearOptions(context: Context): List<AllSeriesFilterOption> {
        fun decade(year: Int): String = context.getString(R.string.series_filter_year_decade_format, year)
        return listOf(
            option(context.getString(R.string.series_filter_year_all), "-1"),
            option("2026", "[2026,2027)"),
            option("2025", "[2025,2026)"),
            option("2024", "[2024,2025)"),
            option("2023", "[2023,2024)"),
            option("2022", "[2022,2023)"),
            option("2021", "[2021,2022)"),
            option("2020", "[2020,2021)"),
            option("2019", "[2019,2020)"),
            option("2018", "[2018,2019)"),
            option("2017", "[2017,2018)"),
            option("2016", "[2016,2017)"),
            option("2015", "[2015,2016)"),
            option("2014-2010", "[2010,2015)"),
            option("2009-2005", "[2005,2010)"),
            option("2004-2000", "[2000,2005)"),
            option(decade(90), "[1990,2000)"),
            option(decade(80), "[1980,1990)"),
            option(context.getString(R.string.series_filter_year_earlier), "[,1980)")
        )
    }

    private fun option(title: String, value: String): AllSeriesFilterOption {
        return AllSeriesFilterOption(title = title, value = value)
    }
}
