package org.example.chatft.service;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.example.chatft.model.User;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.function.Consumer;

public class VideoCallService {
    
    private Webcam webcam;
    private volatile boolean isStreaming = false;
    private Thread captureThread;
    
    private ImageView localVideoView;
    private ImageView remoteVideoView;
    
    private final User remoteUser;
    private final Consumer<String> onSdpOfferReady;
    private final Consumer<String> onSdpAnswerReady;
    private final Consumer<String> onIceCandidateReady;
    private final Runnable onCallEnded;
    private final Consumer<byte[]> onVideoFrameReady;
    
    public VideoCallService(User remoteUser,
                           Consumer<String> onSdpOfferReady,
                           Consumer<String> onSdpAnswerReady, 
                           Consumer<String> onIceCandidateReady,
                           Runnable onCallEnded,
                           Consumer<byte[]> onVideoFrameReady) {
        this.remoteUser = remoteUser;
        this.onSdpOfferReady = onSdpOfferReady;
        this.onSdpAnswerReady = onSdpAnswerReady;
        this.onIceCandidateReady = onIceCandidateReady;
        this.onCallEnded = onCallEnded;
        this.onVideoFrameReady = onVideoFrameReady;
        
        System.out.println("[VIDEO-UDP] VideoCallService initialized for UDP streaming");
    }
    
    public void setLocalVideoView(ImageView view) {
        this.localVideoView = view;
    }
    
    public void setRemoteVideoView(ImageView view) {
        this.remoteVideoView = view;
    }
    
    public void startLocalCamera() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                System.err.println("[VIDEO-UDP-ERR] No webcam found");
                return;
            }
            
            webcam.setViewSize(new java.awt.Dimension(176, 144)); // Smallest supported by webcam
            webcam.open();
            
            isStreaming = true;
            
            // Thread to capture and send video via UDP
            captureThread = new Thread(() -> {
                while (isStreaming && webcam.isOpen()) {
                    try {
                        BufferedImage image = webcam.getImage();
                        if (image != null) {
                            // Display locally
                            if (localVideoView != null) {
                                displayLocalVideo(image);
                            }
                            
                            // Compress heavily for UDP  
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            
                            // Low quality JPEG for small size
                            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
                            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                            param.setCompressionQuality(0.3f);
                            
                            writer.setOutput(ImageIO.createImageOutputStream(baos));
                            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
                            writer.dispose();
                            
                            byte[] imageData = baos.toByteArray();
                            
                            // Debug: Log frame size
                            System.out.println("[VIDEO-UDP] Frame captured: " + imageData.length + " bytes");
                            
                            if (onVideoFrameReady != null && imageData.length < 40000) {
                                onVideoFrameReady.accept(imageData);
                                System.out.println("[VIDEO-UDP] Frame sent via callback");
                            } else if (imageData.length >= 40000) {
                                System.err.println("[VIDEO-UDP-SKIP] Frame too large: " + imageData.length + " bytes");
                            } else {
                                System.err.println("[VIDEO-UDP-ERR] Callback is NULL!");
                            }
                        }
                        
                        Thread.sleep(200); // ~5 FPS for UDP bandwidth
                    } catch (Exception e) {
                        if (isStreaming) {
                            System.err.println("[VIDEO-UDP-ERR] Camera capture: " + e.getMessage());
                        }
                    }
                }
            });
            captureThread.start();
            
            System.out.println("[VIDEO-UDP] Local camera started (UDP streaming)");
            
        } catch (Exception e) {
            System.err.println("[VIDEO-UDP-ERR] Failed to start camera: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void createOffer() {
        String offer = "UDP_VIDEO_READY";
        onSdpOfferReady.accept(offer);
        System.out.println("[VIDEO-UDP] Offer created for UDP streaming");
    }
    
    public void handleOffer(String sdp) {
        String answer = "UDP_VIDEO_READY";
        onSdpAnswerReady.accept(answer);
        System.out.println("[VIDEO-UDP] Answer sent for UDP streaming");
    }
    
    public void handleAnswer(String sdp) {
        System.out.println("[VIDEO-UDP] Answer received, ready for streaming");
    }
    
    public void addIceCandidate(String candidateJson) {
        // Not used in UDP implementation
    }
    
    public void handleIncomingVideoFrame(byte[] frameData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(frameData);
            BufferedImage image = ImageIO.read(bais);
            
            if (image != null) {
                displayRemoteVideo(image);
            }
        } catch (Exception e) {
            System.err.println("[VIDEO-UDP-ERR] Failed to decode frame: " + e.getMessage());
        }
    }
    
    private void displayLocalVideo(BufferedImage image) {
        Platform.runLater(() -> {
            try {
                WritableImage fxImage = new WritableImage(image.getWidth(), image.getHeight());
                PixelWriter pixelWriter = fxImage.getPixelWriter();
                
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        pixelWriter.setArgb(x, y, image.getRGB(x, y));
                    }
                }
                
                localVideoView.setImage(fxImage);
            } catch (Exception e) {
                // Ignore if view is disposed
            }
        });
    }
    
    private void displayRemoteVideo(BufferedImage image) {
        Platform.runLater(() -> {
            try {
                if (remoteVideoView == null) return;
                
                WritableImage fxImage = new WritableImage(image.getWidth(), image.getHeight());
                PixelWriter pixelWriter = fxImage.getPixelWriter();
                
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        pixelWriter.setArgb(x, y, image.getRGB(x, y));
                    }
                }
                
                remoteVideoView.setImage(fxImage);
            } catch (Exception e) {
                // Ignore
            }
        });
    }
    
    public void toggleMute() {
        System.out.println("[VIDEO-UDP] Audio mute toggled (not implemented)");
    }
    
    public void toggleCamera() {
        if (webcam != null) {
            if (webcam.isOpen()) {
                webcam.close();
                isStreaming = false;
                System.out.println("[VIDEO-UDP] Camera stopped");
            } else {
                webcam.open();
                isStreaming = true;
                System.out.println("[VIDEO-UDP] Camera started");
            }
        }
    }
    
    public void endCall() {
        isStreaming = false;
        
        if (captureThread != null) {
            captureThread.interrupt();
        }
        
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        
        System.out.println("[VIDEO-UDP] Call ended");
        
        Platform.runLater(() -> {
            if (onCallEnded != null) {
                onCallEnded.run();
            }
        });
    }
}
