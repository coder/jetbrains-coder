package com.coder.gateway.util

// These are keys that we support in our Gateway links and must not be changed.
private const val TYPE = "type"
const val URL = "url"
const val TOKEN = "token"
const val WORKSPACE = "workspace"
const val OWNER = "owner"
const val AGENT_NAME = "agent"
const val AGENT_ID = "agent_id"
private const val FOLDER = "folder"
private const val IDE_DOWNLOAD_LINK = "ide_download_link"
private const val IDE_PRODUCT_CODE = "ide_product_code"
private const val IDE_BUILD_NUMBER = "ide_build_number"
private const val IDE_PATH_ON_HOST = "ide_path_on_host"

// Helper functions for reading from the map.  Prefer these to directly
// interacting with the map.

fun Map<String, String>.isCoder(): Boolean = this[TYPE] == "coder"

fun Map<String, String>.url() = this[URL]

fun Map<String, String>.token() = this[TOKEN]

fun Map<String, String>.workspace() = this[WORKSPACE]

fun Map<String, String>.owner() = this[OWNER]

fun Map<String, String?>.agentName() = this[AGENT_NAME]

@Deprecated("Use the agent name instead")
fun Map<String, String?>.agentID() = this[AGENT_ID]

fun Map<String, String>.folder() = this[FOLDER]

fun Map<String, String>.ideDownloadLink() = this[IDE_DOWNLOAD_LINK]

fun Map<String, String>.ideProductCode() = this[IDE_PRODUCT_CODE]

fun Map<String, String>.ideBuildNumber() = this[IDE_BUILD_NUMBER]

fun Map<String, String>.idePathOnHost() = this[IDE_PATH_ON_HOST]
