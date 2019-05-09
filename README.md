# PixivforMuzei3
Pixiv plugin for the new Muzei 3 API

Inspired by https://github.com/dahlia/muzei-pixiv, the aim of this app is to  add compatability with the new Muzei 3 API while also improving functionality

Uses official API endpoints of Pixiv.net

Features
  - Can pull pictures from rankings, feed, or bookmarks
    - Up to 50 pictures from the latest rankings
    - Up to 30 pictures from the latest feed or bookmarks
  - Can toggle the display of manga or R18 pictures (dependent on artist properly tagging their submissions)
  - Utilises the OAuth2 authentication protocol; yoru inbox will not be filled with emails from Pixiv warning on a new login
  - Correctly handles the display of albums, displaying only the first image

Future features
  - Granular NSFW level filtering
