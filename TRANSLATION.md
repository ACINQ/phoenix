# Translations

Phoenix is designed to easily support multiple localisations. The process is - for now - handled via GitHub pull requests.

## What to translate

String resources are contained in a `strings.xml` file. The default file, in english, can be found [here](https://github.com/ACINQ/phoenix/blob/master/app/src/main/res/values/strings.xml).

## How to add a new language

1. Fork and clone this project ([How-to](https://git-scm.com/book/en/v2/GitHub-Contributing-to-a-Project))
1. Create a new branch, for example `translate_mylanguagecode`, where my `mylanguagecode` could be `fr` or `it`.
2. Create a new folder for this language, named `values-mylanguagecode`, in `app/src/main/res`. For example, for french you would create a `values-fr` folder.
3. Copy the `app/src/main/res/values/strings.xml` file into this new folder, and in this new file, remove all the lines marked with `translatable="false"`
4. You can now start the translation work proper.

Note that Android Studio offers a [Translation Editor](https://developer.android.com/studio/write/translations-editor) to make this somewhat easier.

## Guidelines

#### Dynamic content

Some content is dynamic: a part of it will contain a dynamic value, such as an amount, that will be injected by the application at runtime. In that case, these strings will contain a special data formatted like this: `%1$s`.

Example:

```xml
<string name="some_dynamic_content">Dynamic content, showing an amount %1$s that was requested by %2$s.</string>
```

Here you have 2 dynamic values: `%1$s` and `%2$s`. These values must not be modified, and the translation must make sure that the order is consistent. Translated in french, we would have:

 ```xml
 <string name="some_dynamic_content">Contenu dynamique affichant un montant de %1$s qui a été demandé par %2$s.</string>
 ```

#### Special characters

Some characters such as the quote `'` character must be escaped using the `\` character.

Example:

```xml
 <string name="escape_this">Don\'t forget to escape quotes</string>
 ```

#### Html content

Some strings are interpreted as HTML content, which is useful if we want to apply some style to a certain part of the string.

Example:

```xml
<string name="scan_amountless_legacy_message"><![CDATA[
    This invoice doesn\'t include an amount. This may be dangerous: malicious nodes may be able to steal your payment. To be safe, you should <b>ask the payee to specify an amount</b> in the payment request.
    <br /><br />
    Are you sure you want to pay this invoice?
  ]]></string>
```

This warning message emphasizes the `ask the payee to specify an amount` part by using the `<b>` bold HTML markups. The translation should try to respect this intent as much as possible.

The `<![CDATA[` and `]]>` at the start/end of the strings must not be removed.

#### Untranslatable content

A few strings must not be translated:
- it would not make sense: for example, the "lorem ipsum..." placeholder.
- it's not ready: if a feature has been committed to master but is still in development, do not translate it because it's likely that the layout will change.

You will know that a string must not be translated when it has the `translatable="false"` attribute.

Also, some technical terms can be hard to translate properly (like blockchain or seed) and it might just be better to keep the english word.

#### Fallback language

English is the default language of the app. This means that if a phrase is not found in the localized `strings.xml`, then the application will display the english phrase.

## Submit the translation

Once your translation is ready, open a pull request ([How-to](https://git-scm.com/book/en/v2/GitHub-Contributing-to-a-Project)). Your translation will be reviewed by the maintainers and contributors to this project.

## Translation approval

Merging the translation is not guaranteed. Having third party contributors proficient in the language, and who can review the translation will increase the chance that the translation is added to the project.

Note that the maintainers of this project can review English, French, Italian, Spanish and German translations. For other languages it would really help to have a third party review.
