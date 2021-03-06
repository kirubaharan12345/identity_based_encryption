/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.suai.ibemailet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;
import org.apache.mailet.RFC2822Headers;
import org.suai.idbased.Client;
import org.suai.idbased.DecryptException;
import org.suai.idbased.PKG;
import org.suai.idbased.Util;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 *
 * @author foxneig
 */
public class DecryptLetter extends GenericMailet {

    private MailetConfig config;
    private String mpk_path;
    private String msk1_path;
    private String msk2_path;
    private PKG pkg;
    private Client client;

    @Override
    public void destroy() {
        System.out.println("Destroy");

    }

    @Override
    public String getMailetInfo() {
        return "IdBasedDecrypt Mailet";
    }

    @Override
    public MailetConfig getMailetConfig() {
        return config;
    }

    @Override
    public void init(MailetConfig config) throws MessagingException {

        System.out.println("Init IdBasedDecryptMailet");
        super.init(config);
        MailetContext context = config.getMailetContext();
        mpk_path = getInitParameter("mpkPath");
        msk1_path = getInitParameter("msk1Path");
        msk2_path = getInitParameter("msk2Path");
        pkg = new PKG();
        try {
            pkg.init(Util.readKeyData(new FileInputStream(mpk_path)), Util.readKeyData(new FileInputStream(msk1_path)), Util.readKeyData(new FileInputStream(msk2_path)));
        } catch (IOException ex) {
            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
        }
      





    }

    public byte[] getAttachments(InputStream is) throws IOException {


        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buff = new byte[8];
        int i = 0;
       
            i = is.read(buff);
             while (i!=-1) {
                 bos.write(buff,0,i);
                 i = is.read(buff);
             }

        try {
            is.close();
        } catch (IOException ex) {
            Logger.getLogger(EncryptLetter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return bos.toByteArray();



    }

    @Override
    public void service(Mail mail) throws MessagingException {
        client = new Client();
        byte[] decrypted = null;
        InputStream is = null;
        Multipart mp = null;
        byte[] body = null;
        ByteArrayInputStream bin = null;
        String text = null;
        BASE64Encoder enc = new BASE64Encoder();
        BASE64Decoder dec = new BASE64Decoder();

        MimeMessage message = mail.getMessage();
        String contentType = message.getContentType();
        System.out.println(contentType);
        MailAddress from = mail.getSender();
        Collection to = mail.getRecipients();
        Iterator<MailAddress> iterator = to.iterator();
        String recip = iterator.next().toString();
        String sender = from.toString();
        System.out.println("E-mail FROM: " + sender);
        System.out.println("E-mail TO: " + recip);
        if (message.isMimeType("text/plain")) {
            try {
                text = (String) message.getContent();
            } catch (IOException ex) {
                Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {

                body = dec.decodeBuffer(text);
            } catch (IOException ex) {
                Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            }
            bin = new ByteArrayInputStream(body);
            System.out.println("Decrypt mail body...");
            try {
                try {
                    decrypted = client.decryptData(bin, recip, sender, pkg.keyExtract(recip), pkg.getMPK(), pkg.getSigningPublicKey());
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                } catch (DecryptException ex) {
                    Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(EncryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchPaddingException ex) {
                Logger.getLogger(EncryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidKeyException ex) {
                Logger.getLogger(EncryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(EncryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalBlockSizeException ex) {
                Logger.getLogger(EncryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (BadPaddingException ex) {
                Logger.getLogger(EncryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("Done");
            String plaintext = new String(decrypted);
            System.out.println (plaintext);
            message.setContent(plaintext, contentType);
            message.setHeader(RFC2822Headers.CONTENT_TYPE, contentType);
            message.saveChanges();


        } else if (message.isMimeType("multipart/mixed") || message.isMimeType("multipart/related") || message.isMimeType("multipart/alternative")) {

            try {
                mp = (Multipart) message.getContent();
            } catch (IOException ex) {
                Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
            }

            for (int i = 0, n = mp.getCount(); i < n; i++) {
                Part part = mp.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    System.out.println("Try to decrypt text");
                    try {
                        text = (String) part.getContent();
                       

                    } catch (IOException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {
                      
                        body = dec.decodeBuffer(text);
                       
                    } catch (IOException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    bin = new ByteArrayInputStream(body);
                    try {
                       
                        decrypted = client.decryptData(bin, recip, sender, pkg.keyExtract(recip), pkg.getMPK(), pkg.getSigningPublicKey());
                       
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (NoSuchAlgorithmException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (NoSuchPaddingException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (InvalidKeyException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IllegalBlockSizeException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (BadPaddingException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (DecryptException ex) {
                        Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    String decr = new String(decrypted);
                    System.out.println (""+decr);
                    part.setContent(decr, part.getContentType());
                   
                   
                    mp.removeBodyPart((BodyPart) part);
                   
                    mp.addBodyPart((BodyPart) part, i);
                  
                    message.setContent(mp);
              
                    
                    

                } else {
                    System.out.println ("Get disposition");


                    String disposition = part.getDisposition();
                    System.out.println ("Disposition: "+disposition);
                    if ((disposition != null) && ((disposition.equals(Part.ATTACHMENT) || (disposition.equals(Part.INLINE))))) {
                        InputStream inputStream = null;
                           
                        try {
                            System.out.println ("Getting content");
                            text = null;
                            // text = (String) part.getContent();
                            inputStream = part.getInputStream();
                            System.out.println ();
                        } catch (IOException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        
                        try {
                            System.out.println ("Base64Decoder start");
                            body = dec.decodeBuffer(new String (this.getAttachments(inputStream)));
                        } catch (IOException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        bin = new ByteArrayInputStream(body);
                        System.out.println("Try to decrypt attache");
                        try {
                            System.out.println ("Decrypting!");
                            decrypted = client.decryptData(bin, recip, sender, pkg.keyExtract(recip), pkg.getMPK(), pkg.getSigningPublicKey());
                        } catch (FileNotFoundException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (NoSuchAlgorithmException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (NoSuchPaddingException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (InvalidKeyException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IllegalBlockSizeException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (BadPaddingException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (DecryptException ex) {
                            Logger.getLogger(DecryptLetter.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        System.out.println ("setContent");
                        part.setContent(decrypted, part.getContentType());
                        System.out.println ("Delete old part");
                        mp.removeBodyPart((BodyPart) part);
                        System.out.println ("Add new part");
                        mp.addBodyPart((BodyPart) part, i);
                        System.out.println ("Setting content");
                        message.setContent(mp);
                        System.out.println ("Saving changes");
                        message.saveChanges();
                        System.out.println("Attache is decrypted");



                    }
                }
            }


        }
         message.setHeader(RFC2822Headers.CONTENT_TYPE, contentType);
         message.saveChanges();
           
        

        System.out.println("Ended");






    }
}
