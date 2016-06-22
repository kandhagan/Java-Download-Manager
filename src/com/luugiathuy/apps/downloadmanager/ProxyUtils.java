package com.luugiathuy.apps.downloadmanager;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * Utility class for  proxy usage.
 *  
 * @author kandhagan
 *
 */
public class ProxyUtils {
	static{
		System.setProperty("http.proxyHost", "10.119.20.35");
		System.setProperty("http.proxyPort", "80");
		System.setProperty("https.proxyHost", "10.119.20.35");
		System.setProperty("https.proxyPort", "80");
		
	}
	
	public static void initSystemProxy(){
		Authenticator.setDefault(new Authenticator() {
		    @Override
		    protected PasswordAuthentication getPasswordAuthentication() {
		        if (getRequestorType() == RequestorType.PROXY) {
		            String prot = getRequestingProtocol().toLowerCase();
		            String host = System.getProperty(prot + ".proxyHost", "");
		            System.out.println("Host: " + host);
		            String port = System.getProperty(prot + ".proxyPort", "80");
		            String user = readParam("User Id: ");
		            
		            if (getRequestingHost().equalsIgnoreCase(host)) {
		                if (Integer.parseInt(port) == getRequestingPort()) {
		                    // Seems to be OK.
		                    return new PasswordAuthentication(user, readParam("Password: ").toCharArray());  
		                }
		            }
		        }
		        return null;
		    }
		    private String readParam(String param){
		        Console c=System.console();
		        if (c==null) { //IN ECLIPSE IDE
		            System.out.print(param);
		            InputStream in=System.in;
		            int max=50;
		            byte[] b=new byte[max];

		            try{
			            int l= in.read(b);
			            
			            l--;//last character is \n
			            if (l>0) {
			                byte[] e=new byte[l];
			                System.arraycopy(b,0, e, 0, l);
			                return new String(e);
			            } else {
			                return null;
			            }
		            }
		            catch(IOException ioe){
		            	return null;
		            }
		        } else { //Outside Eclipse IDE
		            return new String(c.readPassword(param));
		        }
		    }
		});
	}
}
