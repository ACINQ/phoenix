This document provides guidelines for localizing the iOS application.

## How to add a new language

1. Fork and clone this project ([How-to](https://git-scm.com/book/en/v2/GitHub-Contributing-to-a-Project))
2. Create a new branch, for example `translate_mylanguagecode`, where my `mylanguagecode` could be `fr` or `it`.
3. In XCode, select the root project file and go to the project panel, Info tab. Find the localization section and click the `+` button to add your desired language. Then select all the resource files in the list (including the `.html` files).
4. A copy of each file has been created for the new language. You can now start the translation work proper.

## Guidelines

#### Dynamic content

Some content is dynamic: a part of it will contain a dynamic value, such as an amount, that will be injected by the application at runtime. In that case, these strings will contain a special data formatted like this: `%@`, `%s`, `%1$`... This part must not be translated.

#### Additional guidelines

See [here](https://github.com/ACINQ/phoenix/blob/master/TRANSLATION.md#general-considerations).