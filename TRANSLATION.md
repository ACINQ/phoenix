# Translations

Phoenix is designed to easily support multiple localisations. The process is - for now - handled via GitHub pull requests.

## What to translate

There are currently two versions of Phoenix: the Android app and the iOS app. Both apps use different string resources and are localized separately. Their localization processes are completely different.
- follow [these instructions](https://github.com/ACINQ/phoenix/blob/master/phoenix-legacy/TRANSLATION.md) to localize the legacy Android app;
- follow [these instructions](https://github.com/ACINQ/phoenix/blob/master/phoenix-ios/TRANSLATION.md) to localize the iOS app.

## General considerations

#### Untranslatable content

Some strings should not be translated:
- it would not make sense: for example, the "lorem ipsum..." placeholder.
- it's not ready: the feature is still in development (even though it may be on `master` already).

On Android, you will know that a string must not be translated when it has the `translatable="false"` attribute.

Also, some technical terms can be hard to translate properly (like blockchain or seed) and it might just be better to keep the english word.

#### Fallback language

English is the default language of the app. This means that if a string is not translated, then the application will display the english version.

## Submit the translation

Once your translation is ready, open a pull request ([How-to](https://git-scm.com/book/en/v2/GitHub-Contributing-to-a-Project)). Your translation will be reviewed by the maintainers and contributors to this project.

## Translation approval

Merging the translation is not guaranteed. Having third party contributors proficient in the language, and who can review the translation will increase the chance that the translation is added to the project.

Note that the maintainers of this project can review English, French, Spanish and German translations. For other languages it would be very helpful to have a third party review.
