# README

Semantic search demonstration over quantamagazine.org content.

## Requirements
1. JDK 11 or above
2. Maven

## Before you run
1. Create an account and a corpus in [Vectara Console](https://console.vectara.com).
2. Go to [Vectara Console Authentication](https://console.vectara.com/console/authentication) menu and create an App Client
3. Note down the following three things from App Client page in Console.
    1. App Client ID
    2. App Client Secret
    3. Auth URL (This is available near the top of the page)
4. Also note down your Vectara customer/account ID and the Corpus ID (of the corpus you want to index data to, or query.)

## How to run the demo locally
1. Update the configuration file at `src/main/resources/vectara.properties`
2. To crawl and index Quanta Magazine articles for year 2020 (you only need to do this once).
`mvn package -P index`
3. To run the server
`mvn package -P server`
4. Open a browser and go to `https://localhost:8080`
