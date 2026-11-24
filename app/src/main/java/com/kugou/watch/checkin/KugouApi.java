package com.kugou.watch.checkin;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 酷狗音乐概念版（Lite）接口封装：扫码登录 + 每日领 VIP + 升级 VIP。
 */
public final class KugouApi {

    private static final String APPID = "3116";
    private static final String CLIENTVER = "11440";
    private static final String SRCAPPID = "2919";
    private static final String WEB_SALT = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    private static final String LITE_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA";

    private static final String PREFS = "kg_watch_prefs";
    private static final String KEY_MID = "mid";
    private static final String KEY_GUID = "guid";
    private static final String KEY_DEV = "dev";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERID = "userid";
    private static final String KEY_NICK = "nickname";
    private static final String KEY_LAST_RES = "last_result";

    private static final String LITE_RSA_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB";

    private final SharedPreferences prefs;

    public KugouApi(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getString(KEY_MID, "").isEmpty()) {
            prefs.edit().putString(KEY_MID, md5(UUID.randomUUID().toString())).apply();
        }
        if (prefs.getString(KEY_GUID, "").isEmpty()) {
            prefs.edit().putString(KEY_GUID, md5(UUID.randomUUID().toString())).apply();
        }
        if (prefs.getString(KEY_DEV, "").isEmpty()) {
            prefs.edit().putString(KEY_DEV, randAlnum(10).toUpperCase(Locale.US)).apply();
        }
    }

    public boolean isLoggedIn() {
        return !getToken().isEmpty() && !getUserId().isEmpty() && !"0".equals(getUserId());
    }

    public String getToken() { return prefs.getString(KEY_TOKEN, ""); }
    public String getUserId() { return prefs.getString(KEY_USERID, ""); }
    public String getNickname() { return prefs.getString(KEY_NICK, ""); }
    public String getLastResult() { return prefs.getString(KEY_LAST_RES, ""); }

    private String getMid() { return prefs.getString(KEY_MID, ""); }
    private String getGuid() { return prefs.getString(KEY_GUID, ""); }
    private String getDev() { return prefs.getString(KEY_DEV, ""); }

    public void saveAuth(String token, String userId, String nickname) {
        prefs.edit()
            .putString(KEY_TOKEN, token == null ? "" : token)
            .putString(KEY_USERID, userId == null ? "" : userId)
            .putString(KEY_NICK, nickname == null ? "" : nickname)
            .apply();
    }

    public void saveLastResult(String text) {
        prefs.edit().putString(KEY_LAST_RES, text == null ? "" : text).apply();
    }

    public void logout() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USERID).remove(KEY_NICK).remove(KEY_LAST_RES).apply();
    }

    public static final class QrCodeResult {
        public final String key;
        public final Bitmap bitmap;
        public final String error;

        QrCodeResult(String key, Bitmap bitmap, String error) {
            this.key = key;
            this.bitmap = bitmap;
            this.error = error;
        }
    }

    public static final class QrCheckResult {
        public final int status; // 0=过期, 1=待扫码, 2=待确认, 4=成功, -1=网络错误
        public final String token;
        public final String userId;
        public final String nickname;
        public final String error;

        QrCheckResult(int status, String token, String userId, String nickname, String error) {
            this.status = status;
            this.token = token;
            this.userId = userId;
            this.nickname = nickname;
            this.error = error;
        }
    }

    public QrCodeResult fetchQrCode() {
        try {
            TreeMap<String, String> params = baseParams();
            params.put("appid", "1001");
            params.put("type", "1");
            params.put("plat", "4");
            params.put("qrcode_txt", "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=" + APPID + "&");
            params.put("srcappid", SRCAPPID);
            sign(params, WEB_SALT, "");

            JSONObject json = request("GET", "https://login-user.kugou.com/v2/qrcode", params, null, null);
            if (json.optInt("status", 0) != 1) {
                return new QrCodeResult(null, null, "获取二维码失败: " + json.optString("errmsg", json.toString()));
            }
            JSONObject data = json.optJSONObject("data");
            if (data == null) return new QrCodeResult(null, null, "返回数据为空");

            String key = data.optString("qrcode", "");
            String imgData = data.optString("qrcode_img", "");
            int comma = imgData.indexOf(',');
            String b64 = comma >= 0 ? imgData.substring(comma + 1) : imgData;
            byte[] raw = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bmp == null) return new QrCodeResult(null, null, "二维码图片解码失败");
            return new QrCodeResult(key, bmp, null);
        } catch (Exception e) {
            return new QrCodeResult(null, null, "网络错误: " + e.getMessage());
        }
    }

    public QrCheckResult checkQrStatus(String key) {
        try {
            TreeMap<String, String> params = baseParams();
            params.put("plat", "4");
            params.put("appid", APPID);
            params.put("srcappid", SRCAPPID);
            params.put("qrcode", key);
            sign(params, WEB_SALT, "");

            JSONObject json = request("GET", "https://login-user.kugou.com/v2/get_userinfo_qrcode", params, null, null);
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                return new QrCheckResult(-1, null, null, null, json.optString("errmsg", "查询失败"));
            }
            int st = data.optInt("status", -1);
            String token = data.optString("token", "");
            String uid = String.valueOf(data.optLong("userid", 0));
            String nick = data.optString("nickname", "");
            if (st == 4 && !token.isEmpty()) {
                saveAuth(token, uid, nick);
            }
            return new QrCheckResult(st, token, uid, nick, null);
        } catch (Exception e) {
            return new QrCheckResult(-1, null, null, null, e.getMessage());
        }
    }

    public String sendSmsCode(String mobile) {
        try {
            JSONObject body = new JSONObject();
            body.put("businessid", 5);
            body.put("mobile", mobile);
            body.put("plat", 3);
            String bodyStr = body.toString();

            TreeMap<String, String> p = baseParams();
            sign(p, LITE_SALT, bodyStr);
            JSONObject res = request("POST", "http://login.user.kugou.com/v7/send_mobile_code", p, bodyStr, "application/json");
            if (res.optInt("status", 0) == 1 || res.optInt("error_code", -1) == 0) {
                return null; // null means success
            }
            return "发送失败: " + res.optString("errmsg", res.toString());
        } catch (Exception e) {
            return "发送异常: " + e.getMessage();
        }
    }

    public String loginBySms(String mobile, String code) {
        try {
            long ms = System.currentTimeMillis();
            String tempKey = randAlnum(16).toLowerCase(Locale.US);
            String aesKey = md5(tempKey);
            String aesIv = aesKey.substring(aesKey.length() - 16);

            JSONObject plain = new JSONObject();
            plain.put("mobile", mobile);
            plain.put("code", code);
            String encParams = aesEncryptHex(plain.toString(), aesKey, aesIv);

            String maskedMobile = mobile.length() >= 11
                ? mobile.substring(0, 2) + "*****" + mobile.substring(10, 11)
                : mobile;

            String t2 = aesEncryptHex(
                getGuid() + "|0f607264fc6318a92b9e13c65db7cd3c|02:00:00:00:00:00|" + getDev() + "|" + ms,
                "fd14b35e3f81af3817a20ae7adae7020",
                "17a20ae7adae7020"
            );
            String t1 = aesEncryptHex("|" + ms, "5e4ef500e9597fe004bd09a46d8add98", "04bd09a46d8add98");

            JSONObject pkJson = new JSONObject();
            pkJson.put("clienttime_ms", ms);
            pkJson.put("key", tempKey);
            String pk = rsaEncryptZeroPadHex(pkJson.toString()).toUpperCase(Locale.US);

            JSONObject body = new JSONObject();
            body.put("plat", 1);
            body.put("support_multi", 1);
            body.put("t1", t1);
            body.put("t2", t2);
            body.put("clienttime_ms", ms);
            body.put("mobile", maskedMobile);
            body.put("key", md5(APPID + LITE_SALT + CLIENTVER + ms));
            body.put("pk", pk);
            body.put("params", encParams);
            body.put("dfid", "-");
            body.put("dev", getDev());
            body.put("gitversion", "5f0b7c4");
            String bodyStr = body.toString();

            TreeMap<String, String> p = baseParams();
            sign(p, LITE_SALT, bodyStr);
            JSONObject res = request("POST", "https://loginserviceretry.kugou.com/v7/login_by_verifycode", p, bodyStr, "application/json");
            if (res.optInt("status", 0) != 1) {
                return "登录失败: " + res.optString("errmsg", res.toString());
            }
            JSONObject data = res.optJSONObject("data");
            if (data == null) return "登录返回为空";

            String token = data.optString("token", "");
            String uid = String.valueOf(data.optLong("userid", 0));
            String nick = data.optString("nickname", "");
            String secu = data.optString("secu_params", "");
            if (!secu.isEmpty()) {
                String dec = aesDecryptHex(secu, aesKey, aesIv);
                if (dec.startsWith("{")) {
                    JSONObject secObj = new JSONObject(dec);
                    if (token.isEmpty()) token = secObj.optString("token", "");
                    if ("0".equals(uid) && secObj.has("userid")) uid = String.valueOf(secObj.optLong("userid", 0));
                } else if (token.isEmpty()) {
                    token = dec;
                }
            }
            if (token.isEmpty() || "0".equals(uid)) {
                return "未获取到有效 token";
            }
            saveAuth(token, uid, nick);
            return null;
        } catch (Exception e) {
            return "登录异常: " + e.getMessage();
        }
    }

    public String runCheckIn() {
        if (!isLoggedIn()) return "未登录，请先扫码登录";
        StringBuilder sb = new StringBuilder();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        // 1. 每日听歌领 VIP（ad/play_report）
        try {
            long now = System.currentTimeMillis();
            JSONObject body = new JSONObject();
            body.put("ad_id", 12307537187L);
            body.put("play_end", now);
            body.put("play_start", now - 30000L);
            String bodyStr = body.toString();

            TreeMap<String, String> p1 = authParams();
            sign(p1, LITE_SALT, bodyStr);
            JSONObject r1 = request("POST", "https://gateway.kugou.com/youth/v1/ad/play_report", p1, bodyStr, "application/json");
            sb.append("1. 听歌领VIP: ").append(formatResult(r1)).append('\n');
        } catch (Exception e) {
            sb.append("1. 听歌领VIP: 异常(").append(e.getMessage()).append(")\n");
        }

        // 2. 领取今日畅听 VIP（receive_vip_listen_song）
        try {
            TreeMap<String, String> p2 = authParams();
            p2.put("source_id", "90139");
            p2.put("receive_day", today);
            sign(p2, LITE_SALT, "");
            JSONObject r2 = request("POST", "https://gateway.kugou.com/youth/v1/recharge/receive_vip_listen_song", p2, "", "application/x-www-form-urlencoded");
            sb.append("2. 每日畅听VIP: ").append(formatResult(r2)).append('\n');
        } catch (Exception e) {
            sb.append("2. 每日畅听VIP: 异常(").append(e.getMessage()).append(")\n");
        }

        // 3. 升级概念版 VIP（upgrade_vip_reward）
        try {
            TreeMap<String, String> p3 = authParams();
            p3.put("kugouid", getUserId());
            p3.put("ad_type", "1");
            sign(p3, LITE_SALT, "");
            JSONObject r3 = request("POST", "https://gateway.kugou.com/youth/v1/listen_song/upgrade_vip_reward", p3, "", "application/x-www-form-urlencoded");
            sb.append("3. 升级概念VIP: ").append(formatResult(r3)).append('\n');
        } catch (Exception e) {
            sb.append("3. 升级概念VIP: 异常(").append(e.getMessage()).append(")\n");
        }

        // 4. 查询当前概念版 VIP 状态
        try {
            TreeMap<String, String> p4 = authParams();
            p4.put("busi_type", "concept");
            p4.put("opt_product_types", "dvip,qvip");
            p4.put("product_type", "svip");
            sign(p4, LITE_SALT, "");
            JSONObject r4 = request("GET", "https://kugouvip.kugou.com/v1/get_union_vip", p4, null, null);
            sb.append("4. 当前会员: ").append(parseVipStatus(r4));
        } catch (Exception e) {
            sb.append("4. 当前会员: 查询失败(").append(e.getMessage()).append(")");
        }

        String out = sb.toString().trim();
        saveLastResult(out);
        return out;
    }

    private String formatResult(JSONObject json) {
        int st = json.optInt("status", -1);
        int code = json.optInt("error_code", -1);
        String msg = json.optString("errmsg", "");
        if (st == 1 || code == 0) {
            return msg.isEmpty() ? "成功" : "成功(" + msg + ")";
        }
        return (msg.isEmpty() ? "状态 " + code : msg) + " [" + code + "]";
    }

    private String parseVipStatus(JSONObject json) {
        if (json.optInt("status", 0) != 1 && json.optInt("error_code", -1) != 0) {
            return formatResult(json);
        }
        JSONObject data = json.optJSONObject("data");
        if (data == null) return "正常";
        JSONArray busi = data.optJSONArray("busi_vip");
        if (busi != null && busi.length() > 0) {
            for (int i = 0; i < busi.length(); i++) {
                JSONObject item = busi.optJSONObject(i);
                if (item != null && item.optInt("is_vip", 0) == 1) {
                    String end = item.optString("vip_end_time", "");
                    if (!end.isEmpty()) return "VIP有效至 " + end;
                    return "VIP 已生效";
                }
            }
        }
        String end = data.optString("vip_end_time", "");
        if (!end.isEmpty()) return "有效至 " + end;
        return "已请求（未到期或未开通）";
    }

    private TreeMap<String, String> baseParams() {
        TreeMap<String, String> map = new TreeMap<>();
        map.put("dfid", "-");
        map.put("mid", getMid());
        map.put("uuid", "-");
        map.put("appid", APPID);
        map.put("clientver", CLIENTVER);
        map.put("clienttime", String.valueOf(System.currentTimeMillis() / 1000L));
        return map;
    }

    private TreeMap<String, String> authParams() {
        TreeMap<String, String> map = baseParams();
        map.put("token", getToken());
        map.put("userid", getUserId());
        return map;
    }

    private static void sign(TreeMap<String, String> params, String salt, String body) {
        StringBuilder sb = new StringBuilder(salt);
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        if (body != null && !body.isEmpty()) {
            sb.append(body);
        }
        sb.append(salt);
        params.put("signature", md5(sb.toString()));
    }

    private JSONObject request(String method, String baseUrl, TreeMap<String, String> params, String body, String contentType) throws Exception {
        StringBuilder urlSb = new StringBuilder(baseUrl);
        char sep = baseUrl.contains("?") ? '&' : '?';
        for (Map.Entry<String, String> e : params.entrySet()) {
            urlSb.append(sep)
                .append(URLEncoder.encode(e.getKey(), "UTF-8"))
                .append('=')
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            sep = '&';
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlSb.toString()).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);
        String ts = params.get("clienttime");
        conn.setRequestProperty("dfid", "-");
        conn.setRequestProperty("mid", getMid());
        conn.setRequestProperty("clienttime", ts == null ? "0" : ts);
        conn.setRequestProperty("kg-rc", "1");
        conn.setRequestProperty("kg-thash", "5d816a0");
        conn.setRequestProperty("kg-rec", "1");
        conn.setRequestProperty("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F");
        conn.setRequestProperty("User-Agent", "Android11-1070-11440-130-0-LOGIN-wifi");
        if (isLoggedIn()) {
            conn.setRequestProperty("Cookie", "token=" + getToken() + "; userid=" + getUserId() + "; dfid=-; KUGOU_API_MID=" + getMid());
        }
        if (contentType != null) {
            conn.setRequestProperty("Content-Type", contentType);
        }
        if ("POST".equalsIgnoreCase(method)) {
            conn.setDoOutput(true);
            byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder resp = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) resp.append(line);
            }
        }
        conn.disconnect();
        String raw = resp.toString().trim();
        if (raw.isEmpty()) return new JSONObject().put("status", 0).put("errmsg", "HTTP " + code);
        return new JSONObject(raw);
    }

    private static String aesEncryptHex(String plain, String key, String iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
            new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
            new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
        return toHex(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static String aesDecryptHex(String hex, String key, String iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,
            new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
            new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
        byte[] out = cipher.doFinal(fromHex(hex));
        return new String(out, StandardCharsets.UTF_8);
    }

    private static String rsaEncryptZeroPadHex(String plain) throws Exception {
        byte[] keyBytes = Base64.decode(LITE_RSA_B64, Base64.DEFAULT);
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, pub);
        int blockSize = cipher.getBlockSize();
        if (blockSize <= 0) blockSize = 128;
        byte[] src = plain.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[blockSize];
        System.arraycopy(src, 0, padded, 0, Math.min(src.length, blockSize));
        return toHex(cipher.doFinal(padded));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    private static String randAlnum(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
