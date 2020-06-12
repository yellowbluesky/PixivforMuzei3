/*
 *     This file is part of PixivforMuzei3.
 *
 *     PixivforMuzei3 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program  is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.antony.muzei.pixiv.login;

import com.squareup.moshi.Json;

public class OauthResponse
{
    @Json(name = "response")
    private PixivOauthResponse pixivOauthResponse;

    public PixivOauthResponse getPixivOauthResponse()
    {
        return pixivOauthResponse;
    }

    public static class PixivOauthResponse
    {
        private String access_token;
        private int expires_in;
        private String token_type;
        private String scope;
        private String refresh_token;
        private PixivOauthUser user;
        private String device_token;

        public String getAccess_token()
        {
            return access_token;
        }

        public int getExpires_in()
        {
            return expires_in;
        }

        public String getToken_type()
        {
            return token_type;
        }

        public String getScope()
        {
            return scope;
        }

        public String getRefresh_token()
        {
            return refresh_token;
        }

        public PixivOauthUser getUser()
        {
            return user;
        }

        public String getDevice_token()
        {
            return device_token;
        }

        public static class PixivOauthUser
        {
            private Profile_Image_Urls profile_image_urls;
            private String id;
            private String name;
            private String mail_address;
            private boolean is_premius;
            private int x_restrict;
            private boolean is_mail_authorized;

            public Profile_Image_Urls getProfile_image_urls()
            {
                return profile_image_urls;
            }

            public String getId()
            {
                return id;
            }

            public String getName()
            {
                return name;
            }

            public String getMail_address()
            {
                return mail_address;
            }

            public boolean isIs_premius()
            {
                return is_premius;
            }

            public int getX_restrict()
            {
                return x_restrict;
            }

            public boolean isIs_mail_authorized()
            {
                return is_mail_authorized;
            }

            public static class Profile_Image_Urls
            {
                private String px_16x16;
                private String px_50x50;
                private String px_170x170;

                public String getPx_16x16()
                {
                    return px_16x16;
                }

                public String getPx_50x50()
                {
                    return px_50x50;
                }

                public String getPx_170x170()
                {
                    return px_170x170;
                }
            }
        }
    }
}
