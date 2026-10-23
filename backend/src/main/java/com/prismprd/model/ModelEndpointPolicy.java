package com.prismprd.model;

import com.prismprd.common.BizException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

final class ModelEndpointPolicy {
    private ModelEndpointPolicy() {}
    static void check(URI uri) {
        ModelSettingsService.normalizeUrl(uri.toString());
        String host = uri.getHost().toLowerCase();
        if (Set.of("localhost","127.0.0.1","[::1]","::1","host.docker.internal").contains(host)) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || address.getAddress().length==16 && (address.getAddress()[0] & 0xfe)==0xfc)
                    throw new BizException("API 地址指向受限网络；本机模型服务请使用 localhost 或 host.docker.internal");
            }
        } catch (java.net.UnknownHostException e) {
            throw new BizException("无法解析模型 API 地址，请检查域名");
        }
    }
}

