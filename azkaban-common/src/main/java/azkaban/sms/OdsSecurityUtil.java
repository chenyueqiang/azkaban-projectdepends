package azkaban.sms;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * DESede对称加密算法及MD5签名
 **/

public class OdsSecurityUtil {
    /**
     * 密钥算法
     */
    public static final String KEY_ALGORITHM = "DESede";

    /**
     * 密钥算法
     */
    public static final String MD5_ALGORITHM = "MD5";

    /**
     * 加密/解密算法/工作模式/填充方式
     */
    public static final String CIPHER_ALGORITHM = "DESede/ECB/PKCS5Padding";

    /**
     * 密钥长度
     */
    public static final int KEY_SIZE = 168;

    private static final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static final String AESTYPE = "AES/ECB/PKCS5Padding";

    private static final String AES_GEN = "AES";

    /**
     * 加密数据
     *
     * @param data 待加密数据
     * @param key  密钥
     * @return 加密后的数据
     */
    public static String encrypt(String data, String key) throws RuntimeException {
        try {
            //实例化Des密钥
            DESedeKeySpec dks = new DESedeKeySpec(Base64.decodeBase64(key));
            //实例化密钥工厂
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            //生成密钥
            SecretKey secretKey = keyFactory.generateSecret(dks);
            //实例化
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            //初始化，设置为加密模式
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            //执行操作
            byte[] byteData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(byteData);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 解密数据
     *
     * @param data 待解密数据
     * @param key  密钥
     * @return 解密后的数据
     */
    public static String decrypt(String data, String key) throws RuntimeException {
        try {
            //实例化Des密钥
            DESedeKeySpec dks = new DESedeKeySpec(Base64.decodeBase64(key));
            //实例化密钥工厂
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            //生成密钥
            SecretKey secretKey = keyFactory.generateSecret(dks);
            //实例化
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            //初始化，设置为解密模式
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            //执行操作
            byte[] byteData = cipher.doFinal(Base64.decodeBase64(data));
            return new String(byteData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 数据签名
     *
     * @param data 待签名数据
     * @return
     * @throws Exception
     */
    public static String signByMd5(String data) throws RuntimeException {
        try {
            MessageDigest md = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] bytes = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 签名验证
     *
     * @param data 待验证数据
     * @param sign 签名字符串
     * @return
     * @throws Exception
     */
    public static boolean verifySignByMd5(String data, String sign) throws RuntimeException {
        try {
            MessageDigest md = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] bytes = md.digest(data.getBytes(StandardCharsets.UTF_8));
            String signNow = bytesToHex(bytes);
            return Objects.equals(signNow, sign);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * AES2加密
     *
     * @param plainText
     * @param keyStr    16个字符
     * @return
     */
    public static String encryptAES(String plainText, String keyStr) {
        byte[] encrypt = null;
        try {
            Key key = generateKey(keyStr);
            Cipher cipher = Cipher.getInstance(AESTYPE);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            encrypt = cipher.doFinal(plainText.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        String rs = parseByte2HexStr(encrypt);
        return rs;
    }

    /**
     * AES2 解密
     *
     * @param encryptData
     * @param keyStr      16个字符
     * @return
     */
    public static String decryptAES(String encryptData, String keyStr) {
        byte[] decrypt = null;
        try {
            Key key = generateKey(keyStr);
            Cipher cipher = Cipher.getInstance(AESTYPE);
            cipher.init(Cipher.DECRYPT_MODE, key);
            decrypt = cipher.doFinal(parseHexStr2Byte(encryptData));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        try {
            String rs = new String(decrypt, "UTF-8");
            return rs;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage());
        } catch (RuntimeException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static Key generateKey(String key) throws Exception {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
            return keySpec;
        } catch (Exception e) {
            throw e;
        }
    }

    public static JSONObject packageMsg(String plainContent, String desKey, String signKey, String merchantNo, String merchantName, boolean isAES) {
        try {
            String secretContent3Desc = "";
//             System.out.println(String.format("==>明文:{}", plainContent));
            if (isAES) {
                secretContent3Desc = encryptAES(plainContent, desKey);
            } else {
                secretContent3Desc = encrypt(plainContent, desKey);
            }
//             System.out.println(String.format("==>密文:{}", secretContent3Desc));
            String shaSignContent = signByMd5(plainContent + signKey);
//             System.out.println(String.format("==>签名:{}", shaSignContent));

            // 拼装发送数据
            JSONObject jo = new JSONObject();
            jo.put("merchant_no", merchantNo);// 商户号
            jo.put("merchant_name", merchantName);// 商户名称
            jo.put("content", secretContent3Desc);// 加密后内容
            jo.put("sign", shaSignContent);// 签名
            if (isAES) {
                jo.put("encryType", "AES");//加密类型
            }

//             System.out.println(String.format("==>请求报文:{}", jo.toJSONString()));
            return jo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成密钥
     *
     * @return
     * @throws Exception
     */
    private static String initKey() throws Exception {

        //实例化密钥生成器
        KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM);
        //初始化密钥生成器
        kg.init(KEY_SIZE);
        //生成密钥
        SecretKey secretKey = kg.generateKey();
        //获取二进制密钥编码形式
        byte[] key = secretKey.getEncoded();
        return Base64.encodeBase64String(key);
    }

    private static String bytesToHex(byte[] byteArray) {
        char[] resultCharArray = new char[byteArray.length * 2];
        int index = 0;
        for (byte b : byteArray) {
            resultCharArray[index++] = hexDigits[b >>> 4 & 0xf];
            resultCharArray[index++] = hexDigits[b & 0xf];
        }
        return new String(resultCharArray);
    }

    /**
     * 将二进制转换成16进制
     *
     * @param buf
     * @return
     */
    public static String parseByte2HexStr(byte buf[]) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < buf.length; i++) {
            String hex = Integer.toHexString(buf[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toUpperCase());
        }
        return sb.toString();
    }

    /**
     * 将16进制转换为二进制
     *
     * @param hexStr
     * @return
     */
    public static byte[] parseHexStr2Byte(String hexStr) {
        if (hexStr.length() < 1)
            return null;
        byte[] result = new byte[hexStr.length() / 2];
        for (int i = 0; i < hexStr.length() / 2; i++) {
            int high = Integer.parseInt(hexStr.substring(i * 2, i * 2 + 1), 16);
            int low = Integer.parseInt(hexStr.substring(i * 2 + 1, i * 2 + 2),
                    16);
            result[i] = (byte) (high * 16 + low);
        }
        return result;
    }
}
