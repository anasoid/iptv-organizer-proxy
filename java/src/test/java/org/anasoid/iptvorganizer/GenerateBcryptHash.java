package org.anasoid.iptvorganizer;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class GenerateBcryptHash {
  public static void main(String[] args) {
    String password = "nimda$123";
    String hash = BCrypt.withDefaults().hashToString(10, password.toCharArray());
    System.out.println("BCrypt hash of '" + password + "':");
    System.out.println(hash);
  }
}
