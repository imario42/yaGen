package com.github.gekoh.yagen.ddl;

/**
 * @author Georg Kohlweiss
 */
public interface ProfileProvider {

    DDLGenerator.Profile getProfile(String profileName);
}