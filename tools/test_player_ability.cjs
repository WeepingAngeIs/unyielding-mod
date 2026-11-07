const fs=require('fs'),vm=require('vm'),assert=require('assert');
const file='pack-additions/data/mawonna/mega_showdown/showdown/abilities/futakuchionnaplayer.js';
const ability=vm.runInNewContext(fs.readFileSync(file,'utf8'));
const targets=[{volatiles:{}},{volatiles:{}}],boosts=[];
ability.onStart.call({add(){},boost(delta,target){boosts.push({delta,target})}},{foes:()=>targets});
assert.equal(boosts.length,2);
for(const b of boosts){assert.equal(b.delta.atk,-1);assert.equal(b.delta.spa,-1);}
let blocked=0;ability.onStart.call({add(){},boost(){blocked++}},{foes:()=>[{volatiles:{substitute:{}}}]});assert.equal(blocked,0);
console.log('Player ability verified: each foe loses one Attack and Sp. Atk stage; Substitute respected.');
const loadCustom=id=>vm.runInNewContext(fs.readFileSync('pack-additions/data/mawonna/mega_showdown/showdown/abilities/'+id+'.js','utf8'));
for(const [id,stat] of [['inkylove','spe'],['futakuchisgrasp','atk']]) {
  const custom=loadCustom(id), holder={id:'holder'}, attacker={id:'attacker'};
  for(const contact of [false,true]) {
    const changes=[];
    custom.onDamagingHit.call({
      checkMoveMakesContact(move,source,target){assert.equal(source,attacker);assert.equal(target,holder);return contact;},
      add(){},boost(...args){changes.push(args);}
    },20,holder,attacker,{flags:{contact}});
    assert.equal(changes.length,contact?1:0);
    if(contact){assert.deepEqual(JSON.parse(JSON.stringify(changes[0][0])),{[stat]:-1});assert.equal(changes[0][1],attacker);assert.equal(changes[0][2],holder);}
  }
}
// Attraction success/immunity is delegated to native Attract; verify no extra chance roll
// and that failed/noncontact attempts do not report successful attraction.
const charm=loadCustom('taintedcharm');
for(const [contact,attractAllowed] of [[true,true],[true,false],[false,true]]) {
 let attempts=0,announcements=0;const holder={};
 const attacker={addVolatile(name,source){assert.equal(name,'attract');assert.equal(source,holder);attempts++;return attractAllowed;}};
 charm.onDamagingHit.call({effectState:{target:holder},checkMoveMakesContact:()=>contact,add(){announcements++;}},20,holder,attacker,{});
 assert.equal(attempts,contact?1:0);assert.equal(announcements,contact&&attractAllowed?1:0);
}
console.log('Custom contact hooks verified: Grasp Attack -1, Inky Love Speed -1, Tainted Charm native attraction with no chance roll.');
