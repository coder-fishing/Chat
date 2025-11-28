package org.example.chatft.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.chatft.model.User;
import org.example.chatft.service.NetworkService;
import org.example.chatft.service.VideoCallService;

public class VideoCallController {
    
    @FXML private StackPane videoContainer;
    @FXML private ImageView localVideoView;
    @FXML private ImageView remoteVideoView;
    @FXML private Label statusLabel;
    @FXML private Label remoteUserLabel;
    @FXML private Button muteButton;
    @FXML private Button cameraButton;
    @FXML private Button endCallButton;
    
    private VideoCallService videoCallService;
    private NetworkService networkService;
    private User remoteUser;
    private boolean isCaller;
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    
    public void initialize(User remoteUser, NetworkService networkService, boolean isCaller) {
        this.remoteUser = remoteUser;
        this.networkService = networkService;
        this.isCaller = isCaller;
        
        System.out.println("[VIDEO-CALL-DEBUG] Remote user: " + remoteUser.getNickname());
        System.out.println("[VIDEO-CALL-DEBUG] Remote IP: " + remoteUser.getIp());
        System.out.println("[VIDEO-CALL-DEBUG] Is caller: " + isCaller);
        
        remoteUserLabel.setText(isCaller ? 
            "Calling " + remoteUser.getNickname() + "..." : 
            "Incoming call from " + remoteUser.getNickname());
        
        initializeVideoCall();
    }
    
    private void initializeVideoCall() {
        // Bind remote video size to container size
        remoteVideoView.fitWidthProperty().bind(videoContainer.widthProperty());
        remoteVideoView.fitHeightProperty().bind(videoContainer.heightProperty());
        
        videoCallService = new VideoCallService(
            remoteUser,
            // onSdpOfferReady
            sdp -> {
                System.out.println("[VIDEO-UI] Sending SDP Offer to " + remoteUser.getNickname());
                networkService.sendSdpOffer(remoteUser, sdp);
                updateStatus("Connecting...");
            },
            // onSdpAnswerReady
            sdp -> {
                System.out.println("[VIDEO-UI] Sending SDP Answer to " + remoteUser.getNickname());
                networkService.sendSdpAnswer(remoteUser, sdp);
                updateStatus("Connected");
            },
            // onIceCandidateReady
            candidate -> {
                System.out.println("[VIDEO-UI] Sending ICE Candidate to " + remoteUser.getNickname());
                networkService.sendIceCandidate(remoteUser, candidate);
            },
            // onCallEnded
            this::handleCallEnded,
            // onVideoFrameReady - Send video frames via UDP unicast
            frameData -> {
                networkService.sendVideoFrame(remoteUser.getNickname(), remoteUser.getIp(), frameData);
            }
        );
        
        videoCallService.setLocalVideoView(localVideoView);
        videoCallService.setRemoteVideoView(remoteVideoView);
        
        videoCallService.startLocalCamera();
        
        if (isCaller) {
            videoCallService.createOffer();
            updateStatus("Calling...");
        } else {
            updateStatus("Ringing...");
        }
    }
    
    public void handleIncomingSdpOffer(String sdp) {
        System.out.println("[VIDEO-UI] Received SDP Offer");
        updateStatus("Connecting...");
        videoCallService.handleOffer(sdp);
    }
    
    public void handleIncomingSdpAnswer(String sdp) {
        System.out.println("[VIDEO-UI] Received SDP Answer");
        updateStatus("Connected");
        videoCallService.handleAnswer(sdp);
    }
    
    public void handleIncomingIceCandidate(String candidate) {
        System.out.println("[VIDEO-UI] Received ICE Candidate");
        videoCallService.addIceCandidate(candidate);
    }
    
    @FXML
    private void handleMute() {
        isMuted = !isMuted;
        videoCallService.toggleMute();
        muteButton.setText(isMuted ? "🔇" : "🔊");
        updateStatus(isMuted ? "Microphone muted" : "Microphone on");
    }
    
    @FXML
    private void handleCamera() {
        isCameraOn = !isCameraOn;
        videoCallService.toggleCamera();
        cameraButton.setText(isCameraOn ? "📷" : "🚫");
        updateStatus(isCameraOn ? "Camera on" : "Camera off");
    }
    
    @FXML
    private void handleEndCall() {
        videoCallService.endCall();
        networkService.sendCallEnd(remoteUser);
    }
    
    private void handleCallEnded() {
        updateStatus("Call ended");
        Stage stage = (Stage) endCallButton.getScene().getWindow();
        stage.close();
    }
    
    private void updateStatus(String status) {
        javafx.application.Platform.runLater(() -> {
            statusLabel.setText(status);
        });
    }
    
    public VideoCallService getVideoCallService() {
        return videoCallService;
    }
}
