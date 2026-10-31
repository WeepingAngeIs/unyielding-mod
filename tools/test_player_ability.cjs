const fs=require('fs'),vm=require('vm'),assert=require('assert');
const file='pack-additions/data/mawonna/mega_showdown/showdown/abilities/futakuchionnaplayer.js';
const ability=vm.runInNewContext(fs.readFileSync(file,'utf8'));
const targets=[{volatiles:{}},{volatiles:{}}],boosts=[];
ability.onStart.call({add(){},boost(delta,target){boosts.push({delta,target})}},{foes:()=>targets});
assert.equal(boosts.length,2);
for(const b of boosts){assert.equal(b.delta.atk,-1);assert.equal(b.delta.spa,-1);}
let blocked=0;ability.onStart.call({add(){},boost(){blocked++}},{foes:()=>[{volatiles:{substitute:{}}}]});assert.equal(blocked,0);
console.log('Player ability verified: each foe loses one Attack and Sp. Atk stage; Substitute respected.');
