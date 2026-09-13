FROM archlinux:base@sha256:b944cc65c5f28665dfd5fdbf5ed2997c88f5bb4a0aefac7ee8a7ef01893e5ed9
RUN pacman --disable-sandbox -Syu --noconfirm python python-gobject gtk4 sdl2-compat zlib xorg-server-xvfb xorg-xauth dbus imagemagick xorg-xwd xdotool weston && pacman -Scc --noconfirm
ENV PYTHONDONTWRITEBYTECODE=1
