#!/bin/bash

# Récupérer le dossier où se trouve le script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
OUTPUT_FILE="$SCRIPT_DIR/gradle_errors.json"

# Vérifier si un dossier est fourni en argument
if [ "$#" -eq 1 ]; then
    GRADLE_DIR="$1"
    cd "$GRADLE_DIR" || { echo "Erreur : Impossible de se déplacer vers le dossier $GRADLE_DIR"; exit 1; }
else
    GRADLE_DIR="."
fi

# Lancer la commande gradle build et capturer la sortie d'erreur
gradle build 2>&1 | tee gradle_output.log

# Extraire les erreurs et les formater en JSON
ERRORS=$(grep -E '^> Task.*FAILED|^FAILURE: Build failed|^.*error:' gradle_output.log)

# Initialiser le tableau JSON
JSON_ERRORS="["

# Traiter chaque ligne d'erreur
while IFS= read -r line; do
    # Nettoyer la ligne et ajouter au JSON
    cleaned_line=$(echo "$line" | sed 's/"/\\"/g')
    JSON_ERRORS+="{\"message\": \"$cleaned_line\"},"
done <<< "$ERRORS"

# Fermer le tableau JSON (enlever la dernière virgule si nécessaire)
JSON_ERRORS=${JSON_ERRORS%,}
JSON_ERRORS+="]"

# Écrire le résultat dans le fichier de sortie
echo "$JSON_ERRORS" > "$OUTPUT_FILE"

# Afficher le chemin du fichier JSON généré
echo "Les erreurs ont été extraites dans : $OUTPUT_FILE"