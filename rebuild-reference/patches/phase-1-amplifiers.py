#!/usr/bin/env python3
"""Apply the first SC AMPLIFICATION pass to the recovered app-debug(3).apk HTML asset.

This patch intentionally changes only the two amplifier modules. It does not
replace the app shell, decks, browser, or the SC RACK.
"""
from pathlib import Path
import re

asset = Path("app/src/main/assets/index.html")
html = asset.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str) -> None:
    global html
    count = html.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one occurrence for {label}; found {count}")
    html = html.replace(old, new, 1)

# Remove the four per-band CORTE toggles. Amplifier POWER is the only mute control.
html, removed = re.subn(
    r'<button type="button" class="ampBandCut" id="amp(?:Bass|MidBass|Mid|Treble)Cut">CORTE · OFF</button>',
    '',
    html,
)
if removed != 4:
    raise SystemExit(f"Expected to remove four CORTE buttons; removed {removed}")
html, _ = re.subn(r'\.ampBandCut\{[^}]*\}\.ampBandCut\.on\{[^}]*\}', '', html)

replace_once(
    'SC AMPLIFICATION · LOW / BAJOS · 25–180 Hz',
    'SC AMPLIFICATION · BAJOS · 25–125 Hz',
    'LOW amplifier heading',
)
replace_once(
    'SC AMPLIFICATION · MID / HIGH · 180 Hz–20 kHz',
    'SC AMPLIFICATION · MEDIOS / BRILLO',
    'MID/HIGH amplifier heading',
)
replace_once(
    'Crossover independiente de 4º orden: LOW 25–180 Hz · MID/HIGH 180 Hz–20 kHz. Sin mezcla entre vías. Centro = 0 dB · Izquierda = menos · Derecha = más. POWER OFF silencia únicamente su vía.',
    'Dos amplificadores independientes. BAJOS: 25–125 Hz y MEDIO-BAJO. MEDIOS: control izquierdo · BRILLO: control derecho. Centro = 0 dB · Izquierda = menos · Derecha = más. Cada POWER apaga únicamente su amplificador.',
    'amplifier explanatory text',
)
replace_once(
    'let ampCfg={lowOn:true,highOn:true,bass:0,midBass:0,mid:0,treble:0,bassCut:false,midBassCut:false,midCut:false,trebleCut:false};',
    'let ampCfg={lowOn:true,highOn:true,bass:0,midBass:0,mid:0,treble:0};',
    'amplifier configuration defaults',
)
replace_once(
    'smooth(M.bassMute,ampCfg.bassCut?0:1);smooth(M.midBassMute,ampCfg.midBassCut?0:1);smooth(M.midMute,ampCfg.midCut?0:1);smooth(M.trebleMute,ampCfg.trebleCut?0:1);smooth(M.lowAmp,ampCfg.lowOn?1:0);smooth(M.highAmp,ampCfg.highOn?1:0)',
    'smooth(M.bassMute,1);smooth(M.midBassMute,1);smooth(M.midMute,1);smooth(M.trebleMute,1);smooth(M.lowAmp,ampCfg.lowOn?1:0);smooth(M.highAmp,ampCfg.highOn?1:0)',
    'audio routing and independent POWER',
)
replace_once(
    "const cut=!!ampCfg[{bass:'bassCut',midBass:'midBassCut',mid:'midCut',treble:'trebleCut'}[k]];const enabled=(k==='bass'||k==='midBass')?ampCfg.lowOn&&!cut:ampCfg.highOn&&!cut;",
    "const enabled=(k==='bass'||k==='midBass')?ampCfg.lowOn:ampCfg.highOn;",
    'amplifier meter enable state',
)
replace_once(
    "function renderAmpPower(){const a=$('#ampLowPower'),b=$('#ampHighPower');if(a){a.classList.toggle('on',ampCfg.lowOn);a.textContent='⏻ POWER · '+(ampCfg.lowOn?'ON':'OFF')}if(b){b.classList.toggle('on',ampCfg.highOn);b.textContent='⏻ POWER · '+(ampCfg.highOn?'ON':'OFF')}[['ampBassCut','bassCut','BAJOS'],['ampMidBassCut','midBassCut','MEDIO-BAJO'],['ampMidCut','midCut','MEDIOS'],['ampTrebleCut','trebleCut','BRILLO']].forEach(([id,key,label])=>{const e=$('#'+id);if(e){e.classList.toggle('on',ampCfg[key]);e.textContent=ampCfg[key]?'CORTE · ON':'CORTE · OFF'}})}",
    "function renderAmpPower(){const a=$('#ampLowPower'),b=$('#ampHighPower'),low=a?.closest('.ampModule'),high=b?.closest('.ampModule');if(a){a.classList.toggle('on',ampCfg.lowOn);a.setAttribute('aria-pressed',ampCfg.lowOn?'true':'false');a.textContent='⏻ POWER · '+(ampCfg.lowOn?'ON':'OFF')}if(b){b.classList.toggle('on',ampCfg.highOn);b.setAttribute('aria-pressed',ampCfg.highOn?'true':'false');b.textContent='⏻ POWER · '+(ampCfg.highOn?'ON':'OFF')}if(low)low.classList.toggle('ampOff',!ampCfg.lowOn);if(high)high.classList.toggle('ampOff',!ampCfg.highOn)}",
    'amplifier POWER state renderer',
)
replace_once(
    "$('#ampLowPower').onclick=()=>{ampCfg.lowOn=!ampCfg.lowOn;renderAmpPower();applyAmps();saveAmpCfg();msg('SC AMPLIFICATION LOW '+(ampCfg.lowOn?'encendido':'apagado'))};\n$('#ampHighPower').onclick=()=>{ampCfg.highOn=!ampCfg.highOn;renderAmpPower();applyAmps();saveAmpCfg();msg('SC AMPLIFICATION MID/HIGH '+(ampCfg.highOn?'encendido':'apagado'))};$('#ampBassCut').onclick=()=>{ampCfg.bassCut=!ampCfg.bassCut;renderAmpPower();applyAmps();saveAmpCfg();msg('Corte de BAJOS '+(ampCfg.bassCut?'activado':'desactivado'))};$('#ampMidBassCut').onclick=()=>{ampCfg.midBassCut=!ampCfg.midBassCut;renderAmpPower();applyAmps();saveAmpCfg();msg('Corte de MEDIO-BAJO '+(ampCfg.midBassCut?'activado':'desactivado'))};$('#ampMidCut').onclick=()=>{ampCfg.midCut=!ampCfg.midCut;renderAmpPower();applyAmps();saveAmpCfg();msg('Corte de MEDIOS '+(ampCfg.midCut?'activado':'desactivado'))};$('#ampTrebleCut').onclick=()=>{ampCfg.trebleCut=!ampCfg.trebleCut;renderAmpPower();applyAmps();saveAmpCfg();msg('Corte de BRILLO '+(ampCfg.trebleCut?'activado':'desactivado'))};",
    "$('#ampLowPower').onclick=()=>{ampCfg.lowOn=!ampCfg.lowOn;renderAmpPower();applyAmps();saveAmpCfg();msg('SC AMPLIFICATION · BAJOS '+(ampCfg.lowOn?'encendido':'apagado'))};\n$('#ampHighPower').onclick=()=>{ampCfg.highOn=!ampCfg.highOn;renderAmpPower();applyAmps();saveAmpCfg();msg('SC AMPLIFICATION · MEDIOS / BRILLO '+(ampCfg.highOn?'encendido':'apagado'))};",
    'independent amplifier POWER handlers',
)
replace_once(
    'function saveAmpCfg(){try{localStorage.setItem(AMP_CFG_KEY,JSON.stringify(ampCfg))}catch(e){}}',
    "function saveAmpCfg(){try{['bassCut','midBassCut','midCut','trebleCut'].forEach(k=>delete ampCfg[k]);localStorage.setItem(AMP_CFG_KEY,JSON.stringify(ampCfg))}catch(e){}}",
    'legacy cut-state cleanup',
)
replace_once(
    '.ampPower.on{color:#fff;background:linear-gradient(180deg,#164b77,#080d16);border-color:var(--amp-accent);box-shadow:0 0 13px var(--amp-glow),inset 0 0 9px var(--amp-glow)}',
    '.ampPower.on{color:#fff;background:linear-gradient(180deg,#164b77,#080d16);border-color:var(--amp-accent);box-shadow:0 0 13px var(--amp-glow),inset 0 0 9px var(--amp-glow)}\n.ampModule.ampOff{filter:saturate(.45);border-color:#56606d}.ampModule.ampOff .ampMeterFill{width:0!important}.ampModule.ampOff .ampPower{color:#ff9b9b;border-color:#a44;background:linear-gradient(180deg,#49232a,#160b0e);box-shadow:0 0 8px #b33a4a66}',
    'visible amplifier OFF styling',
)

if re.search(r'amp(?:Bass|MidBass|Mid|Treble)Cut|ampBandCut', html):
    raise SystemExit("Obsolete per-band CORTE controls remain in the HTML")
asset.write_text(html, encoding="utf-8")
print("SC AMPLIFICATION phase 1 patch applied.")
