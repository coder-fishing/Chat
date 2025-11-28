# ChatFT - Peer-to-Peer Chat Application

A decentralized chat application built with JavaFX that supports direct messaging, group chats, file transfers, and video calls over local networks.

## Features

### 🔐 Decentralized Architecture
- **No central server required** - Peer-to-peer communication using UDP multicast and TCP
- **Zero-configuration discovery** - Automatic user detection on local network
- **Multi-network support** - Works across LAN, ZeroTier, and other network interfaces

### 💬 Messaging
- **Direct messaging** - Private 1-on-1 conversations
- **Group chats** - Public and private group support
- **Real-time notifications** - Unread message indicators
- **System messages** - Join/leave notifications in groups
- **Message deduplication** - Smart filtering of duplicate messages across network interfaces

### 👥 Group Management
- **Public Groups** - Open for anyone to join
- **Private Groups** - Invitation-based groups
- **Join/Leave functionality** - Easy group participation management
- **Split tab interface** - Separate views for public and private groups
- **Search functionality** - Quickly find users and groups

### 📁 File Transfer
- **Direct file sharing** - Send files to individuals or groups
- **Progress tracking** - Real-time transfer status
- **Resume capability** - Continue interrupted transfers

### 📹 Video Calling
- **WebRTC integration** - High-quality peer-to-peer video calls
- **Screen sharing** - Share your screen during calls
- **Webcam support** - Built-in camera integration

### 🔍 Search & Discovery
- **Unified search** - Single search bar for users and groups
- **Debounced search** - Smooth search experience with 300ms delay
- **Auto-detection** - New users automatically appear in your list

## Technology Stack

- **Java 21** - Modern Java with latest features
- **JavaFX 21** - Rich desktop UI framework
- **WebRTC** - Video calling capabilities
- **UDP Multicast** - User/group discovery and broadcasts
- **TCP** - Reliable message delivery and file transfers
- **Maven** - Dependency management and build system

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Webcam (optional, for video calls)
- Network connection (LAN, ZeroTier, etc.)

## Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ChatFT
