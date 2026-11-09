# Onetune

Privacy-first cross-platform song sharing.

Your friend sent you a "listen to this banger, you'll love it" track, but the link is from a different streaming platform? You enter a "Find out what track and open it in your music streaming app(any%)".

With Onetune you can share tracks with a link that allows your friends to open it in their streaming service or open a link from a some other provider in your streaming service app.

The original idea comes from [FlazeIGuess/unitune](https://github.com/FlazeIGuess/unitune).

## What it does

- **Share**: The app provides a transparent share dialogue that builds a `https://onetune.eincybersimon.de/<platform>/track/<id>` link you can share. When opened, the server provides links for different streaming providers so your friends can directly listen to the track.
- **Open**: You can either open a `https://onetune.eincybersimon.de` link to open the track in your music streaming app, or let Onetune intercept links from different streaming providers and open the track in your music streaming app as well.

## Why Onetune?

- **privacy**: we do not collect any data. There are no accounts, no tracking and no ads. Upon opening a `https://onetune.eincybersimon.de` link, the server resolves the corresponding links for different providers. Head over to [docs/design.md](docs/design.md) if you want to know more.
- **Self-hosting**: The resolver is a small Go server you can host yourself. See [docs/deployment.md](docs/deployment.md) for more information.

## Documentation

- [Development](docs/development.md): Repository structure, information about a local development setup and tests
- [Design](docs/design.md): The overall design/concept for the app.
- [Deployment](docs/deployment.md): Information for self-hosting the server.

## FAQ

### Why is there no option to open the track in Spotify directly?

To generate the actual song link on Spotify, you need to use the Spotify API. However, Spotify [restricted developer access](https://developer.spotify.com/blog/2026-02-06-update-on-developer-access-and-platform-security) and as a result you now need a Spotify Premium account to access their API - which I do not have.
If you want to sponsor an API key for this reach out to me ;)

### Is there an iOS app?

No. The code platform needs some changes to make this work, but there are two problems:
For one, I do not have access to any apple hardware to test. On the other site, this would require an [Apple Developer Program subscription](https://developer.apple.com/programs/) which costs ~99$/year.
If you have access to both reach out to me and we can look into making this an iOS app aswell.

## AI Usage Disclaimer

Most of the initial code for this project was fully vibe-coded.
I have provided the design, audited the code and made some changes, but I want to be transparent about the code state.

## License

0BSD
