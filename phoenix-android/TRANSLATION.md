This document provides guidelines for localizing the **new** Android application.

## What to translate

String resources are contained in two separate files:
- [`important_strings.xml`](https://github.com/ACINQ/phoenix/blob/master/phoenix-legacy/src/main/res/values/important_strings.xml): a "short" file that contains the most important resources, like instructions, guidelines, or error messages.
- [`strings.xml`](https://github.com/ACINQ/phoenix/blob/master/phoenix-legacy/src/main/res/values/strings.xml): contains all the less important resources.

## How to add a new language

1. Fork and clone this project ([How-to](https://git-scm.com/book/en/v2/GitHub-Contributing-to-a-Project))
2. Create a new branch, for example `translate_mylanguagecode`, where my `mylanguagecode` could be `fr` or `it`.
3. Create a new folder for this language, named `values-mylanguagecode`, in `phoenix-android/src/main/res`. For example, for french you would create a `values-fr` folder.
4. Copy the `phoenix-android/src/main/res/values/important_strings.xml` and `phoenix-android/src/main/res/values/important_strings.xml` files into this new folder.
5. You can now start the translation work proper.
6. Then submit a pull request to start the review.

Note that Android Studio offers a [Translation Editor](https://developer.android.com/studio/write/translations-editor) to make this somewhat easier.

## Guidelines

#### Focusing on what's important

As mentioned above, there are 2 files that need translation. `important_strings.xml` should be treated as a priority. It contains high added value resources that explain how the wallet works, or provide critical information. We try to keep this file as small as possible to make the translation work efficient. 

The `strings.xml` file is much larger, and contain technical stuff, or strings that can be understood in context even if not translated. It can be translated later on.

#### Dynamic content

Some content is dynamic: a part of it will contain a dynamic value, such as an amount, that will be injected by the application at runtime. In that case, these strings will contain a special data formatted like this: `%1$s`.

Example:

```xml
<string name="some_dynamic_content">Dynamic content with amount %1$s requested by %2$s.</string>
```

Here you have 2 dynamic values: `%1$s` and `%2$s`. These values must not be modified, and the translation must make sure that the order is consistent. Translated in french, we would have:

 ```xml
 <string name="some_dynamic_content">Contenu dynamique avec montant %1$s demandé par %2$s.</string>
 ```

#### Special characters

Some characters such as the quote `'` character must be escaped using the `\` character.

Example:

```xml
 <string name="escape_this">Don\'t forget to escape quotes</string>
 ```

#### Html content

Some strings contain html styling markups, like `<b>` for bold text, or `<u>` for underlining text.

Example:

```xml
<string name="bold_message">You should <b>ask the payee to specify an amount</b> in the payment request.</string>
```

This warning message emphasizes the `ask the payee to specify an amount` part in bold font. The translation should respect this intent if possible.

#### Additional guidelines

See [here](https://github.com/ACINQ/phoenix/blob/master/TRANSLATION.md#general-considerations).
