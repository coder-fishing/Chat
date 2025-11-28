package org.example.chatft.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.json.JSONObject;

public class APIService {
    private String apiBase;
    private static final int API_PORT = 5001;
    
    public APIService() {
        // Tự động detect: thử localhost trước, nếu không được thì tìm Zerotier IP
        this.apiBase = detectApiServer();
        System.out.println("[API] Using API server: " + apiBase);
    }
    
    public APIService(String apiBaseUrl) {
        this.apiBase = apiBaseUrl;
        System.out.println("[API] Using API server: " + apiBase);
    }
    
    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }
    
    /**
     * Auto-detect API server:
     * 1. Try Zerotier IP first (centralized server)
     * 2. Try localhost (same machine)
     */
    private String detectApiServer() {
        // 1. Try Zerotier subnet first (centralized API server)
        String zerotierSubnet = getZerotierSubnet();
        if (zerotierSubnet != null) {
            System.out.println("[API] Scanning Zerotier subnet: " + zerotierSubnet);
            
            // Try common IPs first (1, 100, 101, 102, 103)
            int[] commonIPs = {100, 101, 102, 103, 104, 105, 1};
            for (int i : commonIPs) {
                String testIP = zerotierSubnet + "." + i;
                String testUrl = "http://" + testIP + ":" + API_PORT;
                
                System.out.println("[API] Testing: " + testUrl);
                if (testConnection(testUrl)) {
                    System.out.println("[API] ✅ Found API server at: " + testUrl);
                    return testUrl;
                }
            }
        }
        
        // 2. Try localhost as fallback
        System.out.println("[API] Testing localhost...");
        if (testConnection("http://localhost:" + API_PORT)) {
            System.out.println("[API] ✅ Using localhost API server");
            return "http://localhost:" + API_PORT;
        }
        
        // 3. Last resort fallback
        System.err.println("[API] ⚠️ Could not detect API server, defaulting to localhost");
        return "http://localhost:" + API_PORT;
    }
    
    /**
     * Get Zerotier subnet (192.168.192 from 192.168.192.103)
     */
    private String getZerotierSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                String name = iface.getDisplayName().toLowerCase();
                
                if (name.contains("zerotier") || name.contains("zt")) {
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof java.net.Inet4Address) {
                            String ip = addr.getHostAddress();
                            String subnet = ip.substring(0, ip.lastIndexOf('.'));
                            System.out.println("[API] Found Zerotier interface: " + name + " (" + ip + ")");
                            return subnet;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[API] Error detecting Zerotier: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Test if API server is reachable
     */
    private boolean testConnection(String apiUrl) {
        try {
            URL url = new URL(apiUrl + "/summarize?text=test");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(500);  // 500ms timeout
            conn.setReadTimeout(500);
            
            int code = conn.getResponseCode();
            conn.disconnect();
            
            return code == 200 || code == 400;  // 400 = server alive but invalid request
        } catch (Exception e) {
            return false;
        }
    }

    public String translate(String text, String srcLang, String tgtLang) throws Exception {
        String urlStr = apiBase + "/translate?text=" + URLEncoder.encode(text, "UTF-8") +
                "&src=" + srcLang + "&tgt=" + tgtLang;

        String response = makeRequest(urlStr);
        JSONObject json = new JSONObject(response);
        return json.getString("translation");
    }

    public String summarize(String text) throws Exception {
        String urlStr = apiBase + "/summarize?text=" + URLEncoder.encode(text, "UTF-8");

        String response = makeRequest(urlStr);
        JSONObject json = new JSONObject(response);
        return json.getString("summary");
    }

    public String[] translateAndSummarize(String text, String srcLang, String tgtLang) throws Exception {
        String urlStr = apiBase + "/translate-summarize?text=" + URLEncoder.encode(text, "UTF-8") +
                "&src=" + srcLang + "&tgt=" + tgtLang;

        String response = makeRequest(urlStr);
        JSONObject json = new JSONObject(response);
        return new String[]{
                json.getString("translation"),
                json.getString("summary")
        };
    }

    private String makeRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // ✅ Tăng timeout cho API calls (HuggingFace chậm)
        conn.setConnectTimeout(10000);  // 10 seconds
        conn.setReadTimeout(60000);     // 60 seconds
        
        System.out.println("[API] Calling: " + urlStr);

        int responseCode = conn.getResponseCode();
        System.out.println("[API] Response code: " + responseCode);
        
        if (responseCode != 200) {
            BufferedReader errReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder errResponse = new StringBuilder();
            String errLine;
            while ((errLine = errReader.readLine()) != null) {
                errResponse.append(errLine);
            }
            errReader.close();
            throw new Exception("API Error " + responseCode + ": " + errResponse.toString());
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        System.out.println("[API] Response: " + response.toString());
        return response.toString();
    }
}