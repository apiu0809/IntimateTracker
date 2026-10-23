package com.mysecret.intimatetracker.domain.model

/** 性别 */
enum class Gender { MALE, FEMALE, OTHER, UNKNOWN }

/** 关系类型（顺序即展示顺序） */
enum class RelationshipType { CASUAL, SPOUSE, GIRLFRIEND, LOVER, FRIEND, EX, COLLEAGUE, STRANGER, OTHER }

/** 主动方 */
enum class Initiator { ME, PARTNER, BOTH }

/** 防护措施 */
enum class Protection { YES, NO, UNKNOWN }

/** 姿势强度 */
enum class Intensity { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
