This document provides guidelines for localizing the **legacy** Android application.

:warning: This legacy Android app is being deprecated in favour of the new `phoenix-android` application. It's better to wait for the new app to be ready before adding new localizations.

## What to translate

All string resources are contained in a `strings.xml` file. The default file, in english, can be found [in phoenix-legacy/src/main/res/values/strings.xml](https://github.com/ACINQ/phoenix/blob/master/phoenix-legacy/src/main/res/values/strings.xml).

## How to add a new language

1. Fork and clone this project ([How-to](https://git-scm.com/book/en/v2/GitHub-Contributing-to-a-Project))
1. Create a new branch, for example `translate_mylanguagecode`, where my `mylanguagecode` could be `fr` or `it`.
2. Create a new folder for this language, named `values-mylanguagecode`, in `phoenix-legacy/src/main/res`. For example, for french you would create a `values-fr` folder.
3. Copy the `phoenix-legacy/src/main/res/values/strings.xml` file into this new folder, and in this new file, remove all the lines marked with `translatable="false"`
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

#### Additional guidelines

See [here](https://github.com/ACINQ/phoenix/blob/master/TRANSLATION.md#general-considerations).
