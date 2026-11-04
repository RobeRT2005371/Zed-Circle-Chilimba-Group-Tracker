# Zed-Circle-Chilimba-Group-Tracker

## Chilimba Tracker

This project is a Java-based group savings tracker for a Chilimba (rotating savings group). It keeps a record of members, contributions, and payouts, calculates the remaining pot, and exposes a simple REST API and HTML front end.

## Project purpose

The application models a rotating savings group in which members contribute money on a regular basis, while the group keeps track of who has paid, how much is in the pot, and who should receive the next payout. The system stores data in a JSON file so it still exists after the server is restarted.

## Members

| Member Name | Member ID |
| --- | --- |
| Bristol Shalowa | 2421003 |
| Emmanuel Sichilima | 2410156 |
| Israel Mumba | 2420971 |
| Robert Sichizya | 2410264 |
| Chitonge Mumba | 2420927 |

## Assignment requirement mapping

The project is designed to satisfy the assignment requirements shown in the brief:

- Real classes: `Member`, `Contribution`, `CashContribution`, `MobileMoneyContribution`, `Payout`, and `ChilimbaGroup` each model a real part of the domain.
- Encapsulation: all important fields in the domain classes are private and are accessed through getter and setter methods with validation.
- Inheritance / interface: `Contribution` is an abstract parent class and `CashContribution` and `MobileMoneyContribution` extend it.
- Polymorphism: `Contribution.getFee()` is declared in the parent class and implemented differently by each subclass. This allows the same method call to behave differently depending on the concrete object type.
- Custom exceptions: `ChilimbaException` is thrown when invalid data is supplied or a business rule is broken, such as duplicate member numbers or payout amounts exceeding the available pot.
- Collections: `ChilimbaGroup` stores `List<Member>`, `List<Contribution>`, and `List<Payout>` to hold group data.
- REST endpoints: the application exposes several API routes, including GET, POST, PUT, and DELETE, for members and contributions.
- Saving data: the app writes the current state to `chilimba_data.json` and reloads it when the program starts.
- Front end: the project includes a simple HTML and JavaScript interface served from `src/main/resources/public/index.html`.

## Main application flow

The project entry point is `com.chilimba.Main`.

1. It creates a `ChilimbaGroup` object.
2. It starts a Javalin web server.
3. It defines routes for members, contributions, payouts, and summary information.
4. It validates incoming JSON and throws `ChilimbaException` for invalid payloads.
5. It stores updates in memory and persists them to the JSON file.

## Core classes

### Member

`Member` stores the member number, name, and phone number.

- It validates empty values.
- It ensures phone numbers follow a reasonable format.
- It acts as the main data object for a group participant.

### Contribution

`Contribution` is an abstract base class for all money contributions.

- It holds the member number, contribution amount, and date.
- It defines the abstract `getFee()` method.
- All subclasses must provide a fee calculation.

### CashContribution

`CashContribution` extends `Contribution` and returns a zero fee because cash contributions do not incur a mobile transaction charge.

### MobileMoneyContribution

`MobileMoneyContribution` extends `Contribution` and adds a transaction reference.

- It validates that the reference is present.
- It calculates a fee of 1% of the contribution amount.

### Payout

`Payout` represents a money transfer made to a member.

- It stores the recipient member number, amount, and payout date.
- It validates that the payout does not exceed the available pot.

### ChilimbaGroup

`ChilimbaGroup` is the service layer that manages group data.

- It stores all members, contributions, and payouts in collections.
- It validates data before adding or updating records.
- It calculates the group total and determines the next payout recipient.
- It loads and saves JSON data from the file `chilimba_data.json`.

## API endpoints

The server exposes the following endpoints:

- GET `/api/members` — list all members
- GET `/api/members/{memberNumber}` — fetch one member
- POST `/api/members` — add a member
- PUT `/api/members/{memberNumber}` — update a member
- DELETE `/api/members/{memberNumber}` — remove a member
- GET `/api/contributions` — list contributions
- GET `/api/contributions/{id}` — fetch one contribution
- POST `/api/contributions` — add a contribution
- PUT `/api/contributions/{id}` — update a contribution
- DELETE `/api/contributions/{id}` — remove a contribution
- GET `/api/payouts` — list payouts
- POST `/api/payouts` — create a payout
- GET `/api/summary` — show the total pot and next recipient

## Data persistence

The application saves the group state to `chilimba_data.json` in the project root. The file stores:

- members
- contributions
- payouts

When the application starts, `ChilimbaGroup.loadData()` checks whether the file exists and restores the previous state automatically.

## Front end

The front end is a simple HTML page that lets the user:

- add a member
- record a contribution
- view the total group pot
- see the next payout recipient
- review member and contribution totals

The page is served from `src/main/resources/public/index.html` and communicates with the Java API using fetch requests.

## Validation and error handling

The application catches invalid request bodies and missing fields, then returns a clear error message in JSON format. Business rules such as duplicate member numbers, invalid phone numbers, and invalid payment values are enforced by the custom `ChilimbaException`.

## Running the project

From the project root, run:

```powershell
$env:CHILIMBA_ADMIN_PASSWORD = Read-Host 'Enter the fixed admin password'
mvn compile exec:java
```

Then open the browser at:

```text
http://localhost:7000/
```

This will start the Javalin server and serve the front-end interface.

Set a strong admin password in the terminal before starting the server. It is read from the `CHILIMBA_ADMIN_PASSWORD` environment variable and is not stored in the repository. Members can create a password once by entering their existing member number and registered phone number; this checks the stored phone number but does not send an SMS verification code. Member passwords are stored as salted PBKDF2 hashes.
