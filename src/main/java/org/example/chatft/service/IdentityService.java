package org.example.chatft.service;

import java.util.UUID;

public class IdentityService {

    private final String peerId;

    public IdentityService() {
        this.peerId = UUID.randomUUID().toString();
    }

    public String getPeerId() {
        return peerId;
    }

    // Hàm tạo discriminator từ peerId
    public String generateDiscriminator() {
        int tag = Math.abs(peerId.hashCode()) % 10000;
        return String.format("%04d", tag); // luôn 4 chữ số
    }

    public String getFullNickname(String nickname) {
        return  generateDiscriminator() + "#" + nickname ;
    }
}
