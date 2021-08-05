package com.Sparrow.Utils.user;

import com.Sparrow.Utils.texture;
import org.to2mbn.jmccc.auth.AuthenticationException;
import org.to2mbn.jmccc.auth.OfflineAuthenticator;
import org.to2mbn.jmccc.auth.yggdrasil.core.Texture;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class offlineUser extends user {

    public offlineUser(String userName) {
        super(userName);
        try {
            this.authenticator = new OfflineAuthenticator(userName);
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
        try {
            this.texture = new texture(new Texture(getClass().getClassLoader().getResource("com/Sparrow/imgs/steve.png").toString(), null));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public offlineUser(){
    }

    public enum textureType{
        STEVE,
        ALEX
    }

    public offlineUser(String userName, textureType textureType) throws UnsupportedEncodingException {
        super(userName);
        try {
            this.authenticator = new OfflineAuthenticator(userName);
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
        try {
            this.texture = new texture(new Texture(getClass().getClassLoader().getResource("com/Sparrow/imgs/steve.png").toString(), null));
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*switch (textureType) {
            case ALEX:
                ((OfflineAuthenticator) this.authenticator).setAlex();
                break;
            case STEVE:
                ((OfflineAuthenticator) this.authenticator).setSteve();
                break;
        }*/
    }

    public boolean haveDefaultTexture() {
        return this.texture.equals(new Texture(getClass().getClassLoader().getResource("com/Sparrow/imgs/steve.png").toString(), null)) || this.texture.equals(new Texture(getClass().getClassLoader().getResource("com/Sparrow/imgs/alex.png").toString(), null));
    }
}
