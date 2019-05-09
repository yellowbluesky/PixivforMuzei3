# PixivforMuzei3
Pixiv plugin for the new Muzei 3 API

Inspired by https://github.com/dahlia/muzei-pixiv, the aim of this app is to  add compatability with the new Muzei 3 API while also improving functionality

Uses official API endpoints of Pixiv.net

Features
  - Can pull pictures from rankings, feed, or bookmarks
    - Up to 50 pictures from the latest rankings
    - Up to 30 pictures from the latest feed or bookmarks
  - Can toggle the display of manga or R18 pictures (dependent on artist properly tagging their submissions)
  - App uses OAuth2 refresh and access tokens, so does not fill your inbox with new login notifications from Pixiv
  - Correctly handles the display of albums, displaying only the first image

Future features
  - Granular NSFW level filtering
