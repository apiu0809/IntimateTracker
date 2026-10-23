package com.mysecret.intimatetracker.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.domain.model.Gender
import com.mysecret.intimatetracker.domain.model.Intensity
import com.mysecret.intimatetracker.domain.model.RelationshipType

@Composable
fun Gender.label(): String = stringResource(
    when (this) {
        Gender.MALE -> R.string.gender_male
        Gender.FEMALE -> R.string.gender_female
        Gender.OTHER -> R.string.gender_other
        Gender.UNKNOWN -> R.string.gender_unknown
    },
)

@Composable
fun RelationshipType.label(): String = stringResource(
    when (this) {
        RelationshipType.CASUAL -> R.string.rel_casual
        RelationshipType.SPOUSE -> R.string.rel_spouse
        RelationshipType.GIRLFRIEND -> R.string.rel_girlfriend
        RelationshipType.LOVER -> R.string.rel_lover
        RelationshipType.FRIEND -> R.string.rel_friend
        RelationshipType.EX -> R.string.rel_ex
        RelationshipType.COLLEAGUE -> R.string.rel_colleague
        RelationshipType.STRANGER -> R.string.rel_stranger
        RelationshipType.OTHER -> R.string.rel_other
    },
)

@Composable
fun Intensity.label(): String = stringResource(
    when (this) {
        Intensity.VERY_LOW -> R.string.intensity_very_low
        Intensity.LOW -> R.string.intensity_low
        Intensity.MEDIUM -> R.string.intensity_medium
        Intensity.HIGH -> R.string.intensity_high
        Intensity.VERY_HIGH -> R.string.intensity_very_high
    },
)
