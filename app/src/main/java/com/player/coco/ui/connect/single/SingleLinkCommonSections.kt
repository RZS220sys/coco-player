package com.player.coco.ui.connect.single

import com.player.coco.data.config.singlelink.SingleLinkValueKeys

object SingleLinkCommonSections {
    fun server(defaultPort: String): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_SERVER,
            title = "Server",
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.ADDRESS, "Address", required = true),
                SingleLinkField.Text(
                    SingleLinkValueKeys.PORT,
                    "Port",
                    defaultValue = defaultPort,
                    required = true,
                    input = TextInput.NUMBER
                ),
            )
        )
    }

    fun usernamePassword(): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_AUTHENTICATION,
            title = "Authentication",
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.USERNAME, "Username"),
                SingleLinkField.Text(SingleLinkValueKeys.PASSWORD, "Password", input = TextInput.PASSWORD),
            )
        )
    }

    fun transport(defaultSecurity: String = "none"): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_TRANSPORT,
            title = "Transport",
            fields = listOf(
                SingleLinkField.Select(
                    SingleLinkValueKeys.STREAM_SECURITY,
                    "Security",
                    listOf("none", "tls", "reality"),
                    defaultValue = defaultSecurity,
                ),
                SingleLinkField.Select(
                    SingleLinkValueKeys.NETWORK,
                    "Network",
                    listOf("tcp", "ws", "grpc", "xhttp", "httpupgrade"),
                ),
            )
        )
    }

    fun tlsSettings(): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_TLS,
            title = "TLS Settings",
            visibleWhen = FormCondition.Equals(
                SingleLinkValueKeys.SECTION_TRANSPORT,
                SingleLinkValueKeys.STREAM_SECURITY,
                "tls"
            ),
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.SNI, "SNI"),
                SingleLinkField.Text(SingleLinkValueKeys.ALPN, "ALPN"),
                SingleLinkField.Text(SingleLinkValueKeys.FINGERPRINT, "Fingerprint"),
                SingleLinkField.Switch(SingleLinkValueKeys.ALLOW_INSECURE, "Allow insecure TLS"),
            )
        )
    }

    fun realitySettings(): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_REALITY,
            title = "Reality Settings",
            visibleWhen = FormCondition.Equals(
                SingleLinkValueKeys.SECTION_TRANSPORT,
                SingleLinkValueKeys.STREAM_SECURITY,
                "reality"
            ),
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.SNI, "SNI"),
                SingleLinkField.Text(SingleLinkValueKeys.FINGERPRINT, "Fingerprint"),
                SingleLinkField.Text(SingleLinkValueKeys.PUBLIC_KEY, "Reality public key", required = true),
                SingleLinkField.Text(SingleLinkValueKeys.SHORT_ID, "Reality short ID"),
                SingleLinkField.Text(SingleLinkValueKeys.SPIDER_X, "Reality spider X"),
            )
        )
    }

    fun websocketSettings(): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_WEBSOCKET,
            title = "WebSocket Settings",
            visibleWhen = FormCondition.Equals(
                SingleLinkValueKeys.SECTION_TRANSPORT,
                SingleLinkValueKeys.NETWORK,
                "ws"
            ),
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.HOST, "Host header"),
                SingleLinkField.Text(SingleLinkValueKeys.PATH, "Path"),
            )
        )
    }

    fun grpcSettings(): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_GRPC,
            title = "gRPC Settings",
            visibleWhen = FormCondition.Equals(
                SingleLinkValueKeys.SECTION_TRANSPORT,
                SingleLinkValueKeys.NETWORK,
                "grpc"
            ),
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.HOST, "Authority"),
                SingleLinkField.Text(SingleLinkValueKeys.PATH, "Service name"),
            )
        )
    }

    fun xhttpSettings(): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_XHTTP,
            title = "xHTTP Settings",
            visibleWhen = FormCondition.Equals(
                SingleLinkValueKeys.SECTION_TRANSPORT,
                SingleLinkValueKeys.NETWORK,
                "xhttp"
            ),
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.HOST, "Host"),
                SingleLinkField.Text(SingleLinkValueKeys.PATH, "Path"),
                SingleLinkField.Text(SingleLinkValueKeys.MODE, "Mode"),
            )
        )
    }

    fun httpUpgradeSettings(): SingleLinkSection {
        return SingleLinkSection(
            id = SingleLinkValueKeys.SECTION_HTTP_UPGRADE,
            title = "HTTP Upgrade Settings",
            visibleWhen = FormCondition.Equals(
                SingleLinkValueKeys.SECTION_TRANSPORT,
                SingleLinkValueKeys.NETWORK,
                "httpupgrade"
            ),
            fields = listOf(
                SingleLinkField.Text(SingleLinkValueKeys.HOST, "Host"),
                SingleLinkField.Text(SingleLinkValueKeys.PATH, "Path"),
            )
        )
    }

    fun transportDetailSections(): List<SingleLinkSection> {
        return listOf(
            tlsSettings(),
            realitySettings(),
            websocketSettings(),
            grpcSettings(),
            xhttpSettings(),
            httpUpgradeSettings(),
        )
    }
}
