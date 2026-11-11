# Room and Hilt generate code that R8 already understands via their bundled rules.
# Keep this file for app-specific rules as the project grows.

# Keep Room entity/DAO metadata used by generated code.
-keep class com.mdibrahim.expensetracker.core.database.** { *; }
