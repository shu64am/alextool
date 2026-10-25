package com.alexmodzofc.tool.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun registeredDomain(host: String): String =
    "https://$host".toHttpUrlOrNull()?.topPrivateDomain() ?: host

