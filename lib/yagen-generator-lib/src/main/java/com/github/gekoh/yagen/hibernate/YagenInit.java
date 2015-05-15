/*
 Copyright 2014 Georg Kohlweiss

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an AS IS BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package com.github.gekoh.yagen.hibernate;

import com.github.gekoh.yagen.ddl.CoreDDLGenerator;
import com.github.gekoh.yagen.ddl.ProfileProvider;

/**
 * @author Georg Kohlweiss
 */
public class YagenInit {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(YagenInit.class);

    public static void init() throws Exception {
        init(null);
    }

    public static void init(Object profile) throws Exception {
        PatchHibernateMappingClasses.applyPatch();

        if (profile == null) {
            PatchGlue.setProfile(null);
            newProfileIfNull();
        }
        else {
            PatchGlue.setProfile(profile);
        }
    }

    public static void newProfileIfNull() {
        if (PatchGlue.getProfile() != null) {
            return;
        }
        try {
            String providerClass = System.getProperty(CoreDDLGenerator.PARAM_PROFILE_PROVIDER_CLASS);
            Object profile;
            if (providerClass != null) {
                profile = ((ProfileProvider)Class.forName(providerClass).newInstance()).getProfile("runtime");
            }
            else {
                profile = ReflectExecutor.i_profile.get().newInstance("runtime");
            }
            PatchGlue.setProfile(profile);
        } catch (Exception e) {
            LOG.error("cannot create profile");
        }
    }

    public static void patch4Transfer() {
        patch4Transfer(null);
    }

    public static void patch4Transfer(Object profile) {
        try {
            init(profile);
            ReflectExecutor.m_patchCollectionsAlwaysLazy.get().invoke(null);
            ReflectExecutor.m_patchIgnoreVersion.get().invoke(null);
        } catch (Exception e) {
            LOG.error("unable to patch for transfer", e);
        }
    }

}