package com.redsurf.tv.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "epg_programs",
    indices = [Index("channelEpgId"), Index("startTime")],
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["epgId"],
            childColumns = ["channelEpgId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long
)
