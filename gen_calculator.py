import yaml, json

with open(r'My coresuit config/CoreSuite/modules/custom-fishing.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

data = {
    'species': config.get('species', {}),
    'baits': config.get('baits', {}),
    'enchants': config.get('enchants', {}),
    'rod_tiers': config.get('rod-tiers', {})
}

html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CoreSuite Fishing Calculator</title>
    <style>
        :root {{
            --bg: #121212;
            --panel: #1e1e1e;
            --text: #e0e0e0;
            --accent: #bb86fc;
            --border: #333;
        }}
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            margin: 0;
            display: flex;
            height: 100vh;
            overflow: hidden;
        }}
        #sidebar {{
            width: 300px;
            background: var(--panel);
            padding: 20px;
            border-right: 1px solid var(--border);
            overflow-y: auto;
        }}
        #main {{
            flex: 1;
            padding: 20px;
            overflow-y: auto;
        }}
        h1, h2, h3 {{ color: var(--accent); margin-top: 0; }}
        label {{ display: block; margin-top: 10px; font-size: 14px; }}
        select, input {{
            width: 100%;
            padding: 8px;
            margin-top: 5px;
            background: #2d2d2d;
            border: 1px solid var(--border);
            color: #fff;
            border-radius: 4px;
        }}
        table {{
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
            font-size: 14px;
        }}
        th, td {{
            text-align: left;
            padding: 8px 12px;
            border-bottom: 1px solid var(--border);
        }}
        th {{ background: #2d2d2d; cursor: pointer; }}
        th:hover {{ background: #3d3d3d; }}
        tr:hover {{ background: #2a2a2a; }}
        .color-a {{ color: #55FF55; }} /* uncommon */
        .color-9 {{ color: #5555FF; }} /* rare */
        .color-5 {{ color: #AA00AA; }} /* epic */
        .color-6 {{ color: #FFAA00; }} /* legendary */
        .color-c {{ color: #FF5555; font-weight: bold; }} /* mythical */
        .color-7 {{ color: #AAAAAA; }} /* common */
    </style>
</head>
<body>
    <div id="sidebar">
        <h2>Settings</h2>
        
        <label>Bait</label>
        <select id="sel_bait">
            <option value="">None</option>
        </select>

        <label>Rod Tier</label>
        <select id="sel_rod">
            <option value="">None (Vanilla)</option>
        </select>

        <label>Lure of the Deep Level (0-3)</label>
        <input type="number" id="in_lure" min="0" max="3" value="0">

        <label>Heavyweight Level (0-5)</label>
        <input type="number" id="in_heavy" min="0" max="5" value="0">

        <label>Luck of the Sea Level (0-3)</label>
        <input type="number" id="in_luck" min="0" max="3" value="0">

        <label>Current Streak</label>
        <input type="number" id="in_streak" min="0" value="0">

        <label>In Legendary Pond?</label>
        <select id="sel_pond">
            <option value="false">No</option>
            <option value="true">Yes</option>
        </select>

        <hr style="border-color: var(--border); margin-top: 20px;">
        <h3>Stats Summary</h3>
        <div id="summary"></div>
    </div>
    
    <div id="main">
        <h1>Catch Probabilities</h1>
        <p>This table shows the true mathematical probability of catching each fish, evaluated from rarest to most common. If no fish is caught, you'll reel in vanilla loot.</p>
        <table id="resultsTable">
            <thead>
                <tr>
                    <th onclick="sortTable(0)">Species</th>
                    <th onclick="sortTable(1)">Rarity</th>
                    <th onclick="sortTable(2)">Base Chance</th>
                    <th onclick="sortTable(3)">Actual Chance (Roll)</th>
                    <th onclick="sortTable(4)">True Probability</th>
                    <th>Weight Range</th>
                </tr>
            </thead>
            <tbody id="resultsBody"></tbody>
        </table>
    </div>

    <script>
        const DATA = {json.dumps(data)};
        
        // Populate Dropdowns
        const baitSel = document.getElementById('sel_bait');
        for (const [key, b] of Object.entries(DATA.baits)) {{
            let opt = document.createElement('option');
            opt.value = key;
            opt.text = (b.name || key).replace(/&[0-9a-fk-or]/gi, '');
            baitSel.appendChild(opt);
        }}
        
        const rodSel = document.getElementById('sel_rod');
        for (const [key, r] of Object.entries(DATA.rod_tiers)) {{
            let opt = document.createElement('option');
            opt.value = key;
            opt.text = (r.name || key).replace(/&[0-9a-fk-or]/gi, '');
            rodSel.appendChild(opt);
        }}

        function formatName(name, rarity) {{
            if (!name) return "Unknown";
            let clean = name.replace(/&[0-9a-fk-or]/gi, '');
            let cls = "color-7";
            switch(rarity.toLowerCase()) {{
                case "uncommon": cls = "color-a"; break;
                case "rare": cls = "color-9"; break;
                case "epic": cls = "color-5"; break;
                case "legendary": cls = "color-6"; break;
                case "mythical": cls = "color-c"; break;
            }}
            return `<span class="${{cls}}">${{clean}}</span>`;
        }}

        let currentSortCol = 4;
        let currentSortDesc = true;

        function sortTable(col) {{
            if (currentSortCol === col) {{
                currentSortDesc = !currentSortDesc;
            }} else {{
                currentSortCol = col;
                currentSortDesc = true;
            }}
            calculate();
        }}

        function calculate() {{
            const baitKey = baitSel.value;
            const rodKey = rodSel.value;
            const lureLvl = parseInt(document.getElementById('in_lure').value) || 0;
            const heavyLvl = parseInt(document.getElementById('in_heavy').value) || 0;
            const luckLvl = parseInt(document.getElementById('in_luck').value) || 0;
            const streak = parseInt(document.getElementById('in_streak').value) || 0;
            const inPond = document.getElementById('sel_pond').value === 'true';

            const activeBait = baitKey ? DATA.baits[baitKey] : null;
            const activeRod = rodKey ? DATA.rod_tiers[rodKey] : null;

            let streakBonus = Math.pow(streak, 0.6) * 0.045;
            let baitCatchBonus = activeBait ? (activeBait['catch-chance-bonus'] || 0.0) : 0.0;
            let baitWeightBonus = activeBait ? (activeBait['weight-bonus'] || 0.0) : 0.0;
            
            let mult = 1.0 + (luckLvl * 0.15) + streakBonus + baitCatchBonus;
            if (mult > 5.0) mult = 5.0; // multCap

            let tierMult = activeRod ? (activeRod['enchant-multiplier'] || 1.0) : 1.0;
            let eventRewardMult = inPond ? 2.0 : 1.0;
            let eventWeightMult = inPond ? 2.0 : 1.0;

            document.getElementById('summary').innerHTML = `
                Global Multiplier: <b>${{mult.toFixed(3)}}x</b><br>
                Rod Tier Multiplier: <b>${{tierMult.toFixed(2)}}x</b><br>
                Pond Active: <b>${{inPond ? "Yes" : "No"}}</b><br>
            `;

            let spArray = [];
            for (const [key, sp] of Object.entries(DATA.species)) {{
                spArray.push({{ key: key, ...sp }});
            }}

            spArray.sort((a, b) => (a.chance || 100.0) - (b.chance || 100.0));

            let probabilityReach = 1.0;
            let results = [];

            for (let sp of spArray) {{
                let baseChance = sp.chance || 100.0;
                let rarity = sp.rarity || "Common";
                
                let actualChance = baseChance * mult;
                actualChance *= (1.0 + (lureLvl * 0.1 * tierMult));

                if (activeBait && activeBait['rarity-bias'] && activeBait['rarity-bias'].toLowerCase() === rarity.toLowerCase()) {{
                    actualChance *= 1.5;
                }}

                let rollChance = actualChance / 100.0;
                if (rollChance > 1.0) rollChance = 1.0;

                let trueProb = probabilityReach * rollChance;
                
                probabilityReach *= (1.0 - rollChance);

                let minWeight = sp['min-weight'] || 0;
                let maxWeight = sp['max-weight'] || 0;
                let wMult = (1.0 + (heavyLvl * 0.05 * tierMult) + baitWeightBonus) * eventWeightMult;
                
                results.push({{
                    name: sp.name || sp.key,
                    rarity: rarity,
                    baseChance: baseChance,
                    actualChance: actualChance,
                    trueProb: trueProb,
                    minW: minWeight * wMult,
                    maxW: maxWeight * wMult
                }});
            }}

            results.push({{
                name: "Vanilla Loot / Nothing",
                rarity: "None",
                baseChance: 0,
                actualChance: 0,
                trueProb: probabilityReach,
                minW: 0,
                maxW: 0
            }});

            results.sort((a, b) => {{
                let valA, valB;
                switch(currentSortCol) {{
                    case 0: valA = a.name; valB = b.name; break;
                    case 1: valA = a.rarity; valB = b.rarity; break;
                    case 2: valA = a.baseChance; valB = b.baseChance; break;
                    case 3: valA = a.actualChance; valB = b.actualChance; break;
                    case 4: valA = a.trueProb; valB = b.trueProb; break;
                }}
                if (valA < valB) return currentSortDesc ? 1 : -1;
                if (valA > valB) return currentSortDesc ? -1 : 1;
                return 0;
            }});

            const tbody = document.getElementById('resultsBody');
            tbody.innerHTML = '';
            for (let r of results) {{
                let tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${{formatName(r.name, r.rarity)}}</td>
                    <td>${{r.rarity}}</td>
                    <td>${{r.baseChance === 0 ? '-' : r.baseChance.toFixed(4) + '%'}}</td>
                    <td>${{r.actualChance === 0 ? '-' : r.actualChance.toFixed(4) + '%'}}</td>
                    <td>${{(r.trueProb * 100).toFixed(4)}}%</td>
                    <td>${{r.minW === 0 && r.maxW === 0 ? '-' : r.minW.toFixed(2) + ' - ' + r.maxW.toFixed(2) + ' kg'}}</td>
                `;
                tbody.appendChild(tr);
            }}
        }}

        document.querySelectorAll('input, select').forEach(el => {{
            el.addEventListener('input', calculate);
        }});

        calculate();

    </script>
</body>
</html>
"""
with open('fishing_calculator.html', 'w', encoding='utf-8') as f:
    f.write(html)
