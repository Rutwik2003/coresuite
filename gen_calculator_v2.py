import yaml, json

with open(r'My coresuit config/CoreSuite/modules/custom-fishing.yml', 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

data = {
    'species': config.get('species', {}),
    'baits': config.get('baits', {}),
    'enchants': config.get('enchants', {}),
    'rod_tiers': config.get('rod-tiers', {}),
    'rewards': config.get('rewards', []),
    'streak_cap': config.get('streak', {}).get('max-multiplier', 10),
    'mult_cap': config.get('streak', {}).get('multiplier-cap', 5.0),
    'fatigue_threshold': config.get('fatigue', {}).get('threshold', 25)
}

html_part1 = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CoreSuite Fishing Simulator & Calculator</title>
    <style>
        :root {
            --bg: #121212;
            --panel: #1e1e1e;
            --text: #e0e0e0;
            --accent: #bb86fc;
            --border: #333;
            --success: #03dac6;
            --danger: #cf6679;
        }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            margin: 0;
            display: flex;
            height: 100vh;
            overflow: hidden;
        }
        #sidebar {
            width: 320px;
            background: var(--panel);
            padding: 20px;
            border-right: 1px solid var(--border);
            overflow-y: auto;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        #main {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .tabs {
            display: flex;
            background: #252525;
            border-bottom: 1px solid var(--border);
        }
        .tab {
            padding: 15px 20px;
            cursor: pointer;
            font-weight: bold;
            color: #888;
            transition: all 0.2s;
        }
        .tab:hover { color: #fff; background: #2a2a2a; }
        .tab.active { color: var(--accent); border-bottom: 2px solid var(--accent); }
        
        .tab-content {
            flex: 1;
            padding: 20px;
            overflow-y: auto;
            display: none;
        }
        .tab-content.active { display: block; }

        h1, h2, h3 { color: var(--accent); margin: 0 0 10px 0; }
        label { display: block; margin-top: 10px; font-size: 13px; font-weight: 600; color: #aaa; }
        select, input, button {
            width: 100%;
            padding: 10px;
            margin-top: 5px;
            background: #2d2d2d;
            border: 1px solid var(--border);
            color: #fff;
            border-radius: 4px;
            box-sizing: border-box;
        }
        button {
            background: var(--accent);
            color: #000;
            font-weight: bold;
            cursor: pointer;
            transition: opacity 0.2s;
            margin-top: 15px;
            font-size: 16px;
        }
        button:hover { opacity: 0.8; }
        
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }
        th, td {
            text-align: left;
            padding: 8px 10px;
            border-bottom: 1px solid var(--border);
        }
        th { background: #2d2d2d; cursor: pointer; position: sticky; top: 0; }
        th:hover { background: #3d3d3d; }
        tr:hover { background: #2a2a2a; }
        
        .color-a { color: #55FF55; } /* uncommon */
        .color-9 { color: #5555FF; } /* rare */
        .color-5 { color: #AA00AA; } /* epic */
        .color-6 { color: #FFAA00; } /* legendary */
        .color-c { color: #FF5555; font-weight: bold; } /* mythical */
        .color-7 { color: #AAAAAA; } /* common */

        .stat-box {
            background: #252525;
            padding: 15px;
            border-radius: 8px;
            border: 1px solid var(--border);
            margin-bottom: 15px;
        }
        .stat-row {
            display: flex;
            justify-content: space-between;
            margin-bottom: 5px;
            font-size: 14px;
        }
        .progress-container {
            width: 100%;
            background: #333;
            border-radius: 10px;
            height: 12px;
            margin-top: 5px;
            overflow: hidden;
        }
        .progress-bar {
            height: 100%;
            background: var(--accent);
            width: 0%;
            transition: width 0.3s ease;
        }

        #log {
            background: #0f0f0f;
            border: 1px solid var(--border);
            padding: 15px;
            border-radius: 8px;
            height: 400px;
            overflow-y: auto;
            font-family: monospace;
            font-size: 14px;
            line-height: 1.5;
        }
        .log-entry { margin-bottom: 5px; border-bottom: 1px dashed #222; padding-bottom: 5px; }
        .log-time { color: #666; font-size: 12px; }
    </style>
</head>
<body>
    <div id="sidebar">
        <h2>Gear & Enchants</h2>
        
        <label>Rod Tier</label>
        <select id="sel_rod">
            <option value="">None (Vanilla)</option>
        </select>

        <label>Active Bait</label>
        <select id="sel_bait">
            <option value="">None</option>
        </select>

        <label>Lure of the Deep Level (0-3)</label>
        <input type="number" id="in_lure" min="0" max="3" value="0">

        <label>Heavyweight Level (0-5)</label>
        <input type="number" id="in_heavy" min="0" max="5" value="0">

        <label>Luck of the Sea Level (0-3)</label>
        <input type="number" id="in_luck" min="0" max="3" value="0">
        
        <label>Double Hook Level (0-3)</label>
        <input type="number" id="in_double" min="0" max="3" value="0">
        
        <label>Treasure Hunter Level (0-3)</label>
        <input type="number" id="in_treasure" min="0" max="3" value="0">

        <label>Tireless Caster Level (0-3)</label>
        <input type="number" id="in_tireless" min="0" max="3" value="0">

        <label>In Legendary Pond?</label>
        <select id="sel_pond">
            <option value="false">No</option>
            <option value="true">Yes</option>
        </select>
        
        <div style="flex:1"></div>
        <div style="font-size: 12px; color: #666; text-align: center;">CoreSuite v2.0</div>
    </div>
    
    <div id="main">
        <div class="tabs">
            <div class="tab active" onclick="switchTab('sim')">Fishing Simulator</div>
            <div class="tab" onclick="switchTab('calc')">Stats & Calculator</div>
        </div>
        
        <div id="sim" class="tab-content active">
            <div style="display: flex; gap: 20px;">
                <div style="flex: 1;">
                    <div class="stat-box">
                        <div class="stat-row">
                            <span style="font-weight: bold; color: var(--accent);">Angler Level <span id="ui_lvl">1</span></span>
                            <span id="ui_xp_text">0 / 20 XP</span>
                        </div>
                        <div class="progress-container">
                            <div id="ui_xp_bar" class="progress-bar"></div>
                        </div>
                    </div>
                    <div class="stat-box">
                        <div class="stat-row">
                            <span>Fishing Streak</span>
                            <span id="ui_streak" style="color: #55FF55; font-weight: bold;">0x</span>
                        </div>
                        <div class="stat-row">
                            <span>Fatigue Score</span>
                            <span id="ui_fatigue">0.0</span>
                        </div>
                        <div class="stat-row">
                            <span>Fatigue Penalty</span>
                            <span id="ui_penalty" style="color: var(--danger);">0%</span>
                        </div>
                        <div class="stat-row">
                            <span>Bait Casts Left</span>
                            <span id="ui_bait_casts" style="color: #FFAA00;">0</span>
                        </div>
                    </div>
                    
                    <button onclick="fish()">?? Cast Rod!</button>
                    <button onclick="rest()" style="background: #333; color: #fff; margin-top: 10px;">?? Rest (Clear Fatigue)</button>
                </div>
                
                <div style="flex: 2;">
                    <h3 style="margin-top:0;">Catch Log</h3>
                    <div id="log"></div>
                </div>
            </div>
        </div>

        <div id="calc" class="tab-content">
            <p>Mathematical probabilities based on your current Gear & Enchants on the left side, as well as your <b>Current Simulated Streak & Fatigue</b>.</p>
            <div class="stat-box">
                <div class="stat-row"><span>Global Multiplier:</span><strong id="calc_mult"></strong></div>
                <div class="stat-row"><span>Rod Multiplier:</span><strong id="calc_rod"></strong></div>
            </div>
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
    </div>

    <script>
        const DATA = """

html_part2 = """ ;
        
        // Populate Dropdowns
        const baitSel = document.getElementById('sel_bait');
        for (const [key, b] of Object.entries(DATA.baits)) {
            let opt = document.createElement('option');
            opt.value = key;
            opt.text = (b.name || key).replace(/&[0-9a-fk-or]/gi, '');
            baitSel.appendChild(opt);
        }
        
        const rodSel = document.getElementById('sel_rod');
        for (const [key, r] of Object.entries(DATA.rod_tiers)) {
            let opt = document.createElement('option');
            opt.value = key;
            opt.text = (r.name || key).replace(/&[0-9a-fk-or]/gi, '');
            rodSel.appendChild(opt);
        }

        // Player State
        let player = {
            level: 1,
            xp: 0,
            streak: 0,
            fatigue: 0.0,
            baitCasts: 0,
            currentBait: ""
        };

        function switchTab(tabId) {
            document.querySelectorAll('.tab-content').forEach(e => e.classList.remove('active'));
            document.querySelectorAll('.tab').forEach(e => e.classList.remove('active'));
            document.getElementById(tabId).classList.add('active');
            event.target.classList.add('active');
            if (tabId === 'calc') updateCalculator();
        }

        function formatName(name, rarity) {
            if (!name) return "Unknown";
            let clean = name.replace(/&[0-9a-fk-or]/gi, '');
            let cls = "color-7";
            if (rarity) {
                switch(rarity.toLowerCase()) {
                    case "uncommon": cls = "color-a"; break;
                    case "rare": cls = "color-9"; break;
                    case "epic": cls = "color-5"; break;
                    case "legendary": cls = "color-6"; break;
                    case "mythical": cls = "color-c"; break;
                }
            }
            return `<span class="${cls}">${clean}</span>`;
        }

        function logMsg(html) {
            const logDiv = document.getElementById('log');
            const d = new Date();
            const time = d.getHours().toString().padStart(2,'0') + ":" + d.getMinutes().toString().padStart(2,'0') + ":" + d.getSeconds().toString().padStart(2,'0');
            const entry = document.createElement('div');
            entry.className = 'log-entry';
            entry.innerHTML = `<span class="log-time">[${time}]</span> ${html}`;
            logDiv.prepend(entry);
        }

        function updateUI() {
            const neededXP = player.level * 20; // fallback xp-formula-multiplier
            
            // Levelup check
            if (player.xp >= neededXP) {
                player.xp -= neededXP;
                player.level++;
                logMsg(`<span style="color:var(--accent);font-weight:bold;">?? LEVEL UP! You are now Angler Level ${player.level}!</span>`);
                return updateUI(); // recursive to update progress
            }

            document.getElementById('ui_lvl').innerText = player.level;
            document.getElementById('ui_xp_text').innerText = `${player.xp} / ${neededXP} XP`;
            document.getElementById('ui_xp_bar').style.width = `${(player.xp / neededXP) * 100}%`;
            document.getElementById('ui_streak').innerText = `${player.streak}x / ${DATA.streak_cap}`;
            document.getElementById('ui_fatigue').innerText = player.fatigue.toFixed(2);
            
            let fatiguePercent = 0.0;
            if (player.fatigue > DATA.fatigue_threshold) {
                fatiguePercent = (player.fatigue - DATA.fatigue_threshold) / 100.0;
                if (fatiguePercent > 1.0) fatiguePercent = 1.0;
            }
            document.getElementById('ui_penalty').innerText = (fatiguePercent * 100).toFixed(1) + "%";

            // Bait logic
            if (baitSel.value !== player.currentBait) {
                player.currentBait = baitSel.value;
                if (player.currentBait) {
                    const b = DATA.baits[player.currentBait];
                    player.baitCasts = b.casts || 20;
                    logMsg(`<span style="color:#55FF55;">Applied bait: ${(b.name || player.currentBait).replace(/&[0-9a-fk-or]/gi, '')}</span>`);
                } else {
                    player.baitCasts = 0;
                }
            }

            if (player.baitCasts <= 0 && player.currentBait !== "") {
                logMsg(`<span style="color:var(--danger);">Your bait ran out!</span>`);
                player.currentBait = "";
                player.baitCasts = 0;
                baitSel.value = "";
            }
            document.getElementById('ui_bait_casts').innerText = player.baitCasts;
        }

        function rest() {
            player.fatigue = 0.0;
            player.streak = 0;
            logMsg(`?? You rested. Fatigue and Streak reset to 0.`);
            updateUI();
        }

        function getCalculatedValues() {
            const lureLvl = parseInt(document.getElementById('in_lure').value) || 0;
            const heavyLvl = parseInt(document.getElementById('in_heavy').value) || 0;
            const luckLvl = parseInt(document.getElementById('in_luck').value) || 0;
            const doubleLvl = parseInt(document.getElementById('in_double').value) || 0;
            const treasureLvl = parseInt(document.getElementById('in_treasure').value) || 0;
            const tirelessLvl = parseInt(document.getElementById('in_tireless').value) || 0;
            const inPond = document.getElementById('sel_pond').value === 'true';

            const activeBait = player.currentBait ? DATA.baits[player.currentBait] : null;
            const activeRod = rodSel.value ? DATA.rod_tiers[rodSel.value] : null;

            let streakBonus = Math.pow(player.streak, 0.6) * 0.045;
            let baitCatchBonus = activeBait ? (activeBait['catch-chance-bonus'] || 0.0) : 0.0;
            let baitWeightBonus = activeBait ? (activeBait['weight-bonus'] || 0.0) : 0.0;
            
            let fatiguePercent = 0.0;
            if (player.fatigue > DATA.fatigue_threshold) {
                fatiguePercent = (player.fatigue - DATA.fatigue_threshold) / 100.0;
                if (fatiguePercent > 1.0) fatiguePercent = 1.0;
            }
            let fatigueMult = 1.0 - fatiguePercent;

            let mult = (1.0 + (luckLvl * 0.15) + streakBonus + baitCatchBonus) * fatigueMult;
            if (mult > DATA.mult_cap) mult = DATA.mult_cap;

            let tierMult = activeRod ? (activeRod['enchant-multiplier'] || 1.0) : 1.0;
            let eventRewardMult = inPond ? 2.0 : 1.0;
            let eventWeightMult = inPond ? 2.0 : 1.0;

            return {
                mult, tierMult, eventRewardMult, eventWeightMult, activeBait, fatiguePercent,
                lureLvl, heavyLvl, doubleLvl, treasureLvl, tirelessLvl, inPond
            };
        }

        function fish() {
            updateUI(); // ensure bait sync
            const vals = getCalculatedValues();
            
            let spArray = [];
            for (const [key, sp] of Object.entries(DATA.species)) {
                spArray.push({ key: key, ...sp });
            }
            spArray.sort((a, b) => (a.chance || 100.0) - (b.chance || 100.0));

            let caught = null;
            let roll = Math.random() * 100.0;

            for (let sp of spArray) {
                let baseChance = sp.chance || 100.0;
                let rarity = sp.rarity || "Common";
                
                let actualChance = baseChance * vals.mult;
                actualChance *= (1.0 + (vals.lureLvl * 0.1 * vals.tierMult));

                if (vals.activeBait && vals.activeBait['rarity-bias'] && vals.activeBait['rarity-bias'].toLowerCase() === rarity.toLowerCase()) {
                    actualChance *= 1.5;
                }

                if (roll <= actualChance) {
                    caught = sp;
                    break;
                } else {
                    roll = Math.random() * 100.0; // new roll for next fish
                }
            }

            if (caught) {
                let minWeight = caught['min-weight'] || 0;
                let maxWeight = caught['max-weight'] || 0;
                let wMult = (1.0 + (vals.heavyLvl * 0.05 * vals.tierMult) + vals.activeBait?.weightBonus || 0.0) * vals.eventWeightMult;
                let weight = minWeight + (maxWeight - minWeight) * Math.random();
                weight *= wMult;

                // Fatigue Math
                let addFatigue = 0.0;
                if (!vals.inPond) {
                    switch ((caught.rarity || "common").toLowerCase()) {
                        case "uncommon": addFatigue = 1.0; break;
                        case "rare": addFatigue = 2.5; break;
                        case "epic": addFatigue = 5.0; break;
                        case "legendary": addFatigue = 15.0; break;
                        case "mythical": addFatigue = 30.0; break;
                        default: addFatigue = 0.5; break;
                    }
                    addFatigue += (weight * 0.02); // The new fixed multiplier!
                    if (vals.tirelessLvl > 0) {
                        let reduction = vals.tirelessLvl * 0.25;
                        addFatigue = addFatigue * (1.0 - reduction);
                    }
                    player.fatigue += addFatigue;
                } else {
                    player.fatigue = Math.max(0, player.fatigue - 5.0); // Healing in pond
                }

                // Streak
                player.streak++;
                if (player.streak > DATA.streak_cap) player.streak = DATA.streak_cap;

                // XP
                let xpMsg = "";
                if (!vals.inPond && player.fatigue > DATA.fatigue_threshold) {
                    xpMsg = `<span style="color:var(--danger); font-size: 11px;"> (Too tired to gain XP)</span>`;
                } else {
                    let gainedXP = 1;
                    if (player.streak > 1 && Math.random() * 100.0 <= (player.streak * 5.0)) {
                        gainedXP += Math.floor(Math.random() * 3) + 1;
                    }
                    
                    let doubleHookStr = "";
                    if (vals.doubleLvl > 0 && (Math.random() * 100.0) <= (vals.doubleLvl * 5.0 * vals.tierMult)) {
                        doubleHookStr = ` <span style="color:yellow; font-weight:bold;">(Double Hook! x2)</span>`;
                        gainedXP *= 2;
                    }
                    
                    player.xp += gainedXP;
                    xpMsg = `<span style="color:#888">+${gainedXP} XP</span>${doubleHookStr}`;
                }

                logMsg(`🎣 You caught a ${formatName(caught.name, caught.rarity)} <span style="color:#aaa">(${weight.toFixed(2)} kg)</span> ${xpMsg}`);
            } else {
                // Check rewards
                let gotReward = false;
                for (let r of DATA.rewards) {
                    let parts = r.split('|');
                    let chance = parseFloat(parts[0]);
                    let rarity = parts[1];
                    let name = parts[2];
                    
                    let actualChance = chance * vals.mult;
                    actualChance *= (1.0 + (vals.treasureLvl * 0.2 * vals.tierMult)) * vals.eventRewardMult;
                    
                    if (Math.random() * 100.0 <= actualChance) {
                        logMsg(`?? <span style="color:var(--accent);font-weight:bold;">You fished up a reward: ${name.replace(/&[0-9a-fk-or]/gi, '')}</span>`);
                        gotReward = true;
                        break;
                    }
                }
                
                if (!gotReward) {
                    logMsg(`?? You caught nothing special. <span style="color:#666">(Vanilla fish/Junk)</span>`);
                    player.streak = 0; // streak resets on failure usually
                }
            }

            if (player.baitCasts > 0) player.baitCasts--;
            updateUI();
            if (document.getElementById('calc').classList.contains('active')) updateCalculator();
        }

        // Calculator Logic
        let currentSortCol = 4;
        let currentSortDesc = true;

        function sortTable(col) {
            if (currentSortCol === col) {
                currentSortDesc = !currentSortDesc;
            } else {
                currentSortCol = col;
                currentSortDesc = true;
            }
            updateCalculator();
        }

        function updateCalculator() {
            const vals = getCalculatedValues();
            document.getElementById('calc_mult').innerText = ` ${vals.mult.toFixed(3)}x (Includes Fatigue: ${(1.0-vals.fatiguePercent).toFixed(2)}x)`;
            document.getElementById('calc_rod').innerText = ` ${vals.tierMult.toFixed(2)}x`;

            let spArray = [];
            for (const [key, sp] of Object.entries(DATA.species)) {
                spArray.push({ key: key, ...sp });
            }
            spArray.sort((a, b) => (a.chance || 100.0) - (b.chance || 100.0));

            let probabilityReach = 1.0;
            let results = [];

            for (let sp of spArray) {
                let baseChance = sp.chance || 100.0;
                let rarity = sp.rarity || "Common";
                
                let actualChance = baseChance * vals.mult;
                actualChance *= (1.0 + (vals.lureLvl * 0.1 * vals.tierMult));

                if (vals.activeBait && vals.activeBait['rarity-bias'] && vals.activeBait['rarity-bias'].toLowerCase() === rarity.toLowerCase()) {
                    actualChance *= 1.5;
                }

                let rollChance = actualChance / 100.0;
                if (rollChance > 1.0) rollChance = 1.0;

                let trueProb = probabilityReach * rollChance;
                probabilityReach *= (1.0 - rollChance);

                let minWeight = sp['min-weight'] || 0;
                let maxWeight = sp['max-weight'] || 0;
                let baitW = vals.activeBait ? (vals.activeBait['weight-bonus'] || 0.0) : 0.0;
                let wMult = (1.0 + (vals.heavyLvl * 0.05 * vals.tierMult) + baitW) * vals.eventWeightMult;
                
                results.push({
                    name: sp.name || sp.key,
                    rarity: rarity,
                    baseChance: baseChance,
                    actualChance: actualChance,
                    trueProb: trueProb,
                    minW: minWeight * wMult,
                    maxW: maxWeight * wMult
                });
            }

            results.push({
                name: "Vanilla Loot / Nothing",
                rarity: "None",
                baseChance: 0,
                actualChance: 0,
                trueProb: probabilityReach,
                minW: 0,
                maxW: 0
            });

            results.sort((a, b) => {
                let valA, valB;
                switch(currentSortCol) {
                    case 0: valA = a.name; valB = b.name; break;
                    case 1: valA = a.rarity; valB = b.rarity; break;
                    case 2: valA = a.baseChance; valB = b.baseChance; break;
                    case 3: valA = a.actualChance; valB = b.actualChance; break;
                    case 4: valA = a.trueProb; valB = b.trueProb; break;
                }
                if (valA < valB) return currentSortDesc ? 1 : -1;
                if (valA > valB) return currentSortDesc ? -1 : 1;
                return 0;
            });

            const tbody = document.getElementById('resultsBody');
            tbody.innerHTML = '';
            for (let r of results) {
                let tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${formatName(r.name, r.rarity)}</td>
                    <td>${r.rarity}</td>
                    <td>${r.baseChance === 0 ? '-' : r.baseChance.toFixed(4) + '%'}</td>
                    <td>${r.actualChance === 0 ? '-' : r.actualChance.toFixed(4) + '%'}</td>
                    <td>${(r.trueProb * 100).toFixed(4)}%</td>
                    <td>${r.minW === 0 && r.maxW === 0 ? '-' : r.minW.toFixed(2) + ' - ' + r.maxW.toFixed(2) + ' kg'}</td>
                `;
                tbody.appendChild(tr);
            }
        }

        document.querySelectorAll('input, select').forEach(el => {
            el.addEventListener('input', () => {
                updateUI();
                if (document.getElementById('calc').classList.contains('active')) updateCalculator();
            });
        });

        updateUI();
    </script>
</body>
</html>
"""

with open('fishing_simulator.html', 'w', encoding='utf-8') as f:
    f.write(html_part1)
    f.write(json.dumps(data))
    f.write(html_part2)

print("fishing_simulator.html generated successfully!")
