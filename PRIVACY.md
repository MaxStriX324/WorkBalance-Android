# WorkBalance Privacy Policy

Last updated: September 8, 2026

[Русская версия](PRIVACY_RU.md)

WorkBalance is an offline-first work-time planning application. It does not require registration.

## Data stored on the device

The app stores the following data in its local Android database and preferences:

- check-in and check-out times;
- daily target and required-break settings;
- manual day types and planned unavailable days;
- the workplace name entered by the user;
- production-calendar options;
- local reminder settings;
- the selected application language;
- the update-check preference in the GitHub distribution.

## Data transmission

WorkBalance contains no analytics, advertising SDK, account system, or proprietary remote server. Work records, workplace names, calendar choices, and reminder settings are not sent to the developer.

The `github` distribution can request the public release list for `MaxStriX324/WorkBalance-Android` from the GitHub API. That request contains normal network metadata visible to GitHub and the installed version in the user-agent string, but no work records. Automatic checks can be disabled.

The `play` and `rustore` distributions omit the built-in update checker and do not request Android's internet permission.

Production-calendar packs are bundled in the application and are not downloaded from a server.

## Export and backup

The user may explicitly export a JSON backup or a monthly CSV report through Android's system file picker. The user selects the destination and is responsible for the exported file after it has been handed to the selected storage provider or application.

A JSON backup contains the entered workplace name and exact work records. It does not contain an Android account, hardware identifier, advertising identifier, badge number, or automatically collected identity information.

If Android system backup is enabled on the device, Android may also copy the app database and preferences to the backup provider configured for that device and may restore them during device transfer or reinstallation. This system backup is controlled by Android and the user’s backup provider, not by the WorkBalance developer.

## Android permissions

- Notifications are used for optional break and forgotten-record reminders.
- Receive boot completed is used only to restore the schedule of local reminders after a restart.
- Internet is present only in the GitHub distribution for public release checks.

The app does not request access to location, contacts, camera, microphone, call history, or SMS.

## Data deletion

Users can delete individual records in the app. Uninstalling WorkBalance or clearing its application data removes its local database and preferences from the device. Previously exported files must be deleted separately from their chosen storage location.

## Sharing diagnostic examples

Before opening an issue, remove employee names, employer names, badge identifiers, real backups, and internal documents. Do not publish a signing key or its passwords.

## Contact

Privacy or security questions may be submitted through the project owner profile: [MaxStriX324](https://github.com/MaxStriX324).
