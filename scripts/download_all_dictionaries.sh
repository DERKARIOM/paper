#!/bin/bash
# download_all_dictionaries.sh - macOS kompatibel

DICT_DIR="/Users/christian/Documents/git/makeacopy/app/src/full/assets/dictionaries"
mkdir -p "$DICT_DIR"
cd "$DICT_DIR"

echo "Downloading dictionaries for all 23 supported languages..."

# Funktion zum Download
download_dict() {
    local tess_code="$1"
    local freq_code="$2"

    echo "  Downloading $tess_code ($freq_code)..."

    curl -s -o "${freq_code}_full.txt" \
        "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/${freq_code}/${freq_code}_full.txt"

    if [ -f "${freq_code}_full.txt" ] && [ -s "${freq_code}_full.txt" ]; then
        cut -d' ' -f1 "${freq_code}_full.txt" | head -50000 > "${tess_code}.txt"
        gzip -9 -f "${tess_code}.txt"
        rm "${freq_code}_full.txt"
        echo "    ✓ Created ${tess_code}.txt.gz"
    else
        echo "    ✗ Failed to download $tess_code"
        rm -f "${freq_code}_full.txt"
    fi
}

# Alle Sprachen herunterladen
download_dict "deu" "de"
download_dict "eng" "en"
download_dict "fra" "fr"
download_dict "spa" "es"
download_dict "ita" "it"
download_dict "por" "pt"
download_dict "nld" "nl"
download_dict "pol" "pl"
download_dict "ces" "cs"
download_dict "slk" "sk"
download_dict "hun" "hu"
download_dict "ron" "ro"
download_dict "dan" "da"
download_dict "nor" "no"
download_dict "swe" "sv"
download_dict "rus" "ru"
download_dict "chi_sim" "zh"
download_dict "tha" "th"
download_dict "ara" "ar"
download_dict "fas" "fa"
download_dict "tur" "tr"
download_dict "hin" "hi"

# chi_tra verwendet dasselbe Wörterbuch wie chi_sim
if [ -f "chi_sim.txt.gz" ]; then
    cp "chi_sim.txt.gz" "chi_tra.txt.gz"
    echo "    ✓ Copied chi_sim.txt.gz to chi_tra.txt.gz"
fi

# Lizenz-Datei erstellen
cat > LICENSE.txt << 'EOF'
Dictionary License Information
==============================

All dictionaries in this folder are derived from FrequencyWords:
https://github.com/hermitdave/FrequencyWords

License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)
https://creativecommons.org/licenses/by-sa/4.0/

Source data: Wikipedia word frequency lists
EOF

echo ""
echo "Done! Files created:"
ls -lh "$DICT_DIR"/*.gz 2>/dev/null
echo ""
echo "Total size:"
du -sh "$DICT_DIR"