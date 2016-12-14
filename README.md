# Scala implementation of the Glicko-2 system

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.andriykuba/scala-glicko2/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.andriykuba/scala-glicko2)

> The Glicko rating system and Glicko-2 rating system are methods for assessing a player's strength in games of skill, such as chess and go. 

[Glicko2 on Wikipedia](https://en.wikipedia.org/wiki/Glicko_rating_system)

> The Glicko-2 rating system is intended to estimate the skill of player performance in head-to-head competiotion in a variety of games.

[Describtion of Glicko-2 system](http://www.glicko.net/ratings/glicko2desc.pdf)

For more details, please, look at [the Mark Glickman site](http://www.glicko.net/)
and the [example of the Glicko-2 system](http://www.glicko.net/glicko/glicko2.pdf)

## Install

Add the library in `built.sbt`
```scala
libraryDependencies += "com.github.andriykuba" % "scala-glicko2" % "1.0.0" 
```

## Usage 

Import classes that is used in a calculation.

```scala
import com.github.andriykuba.scala.glicko2.scala.Glicko2
import com.github.andriykuba.scala.glicko2.scala.Glicko2.{Player, Win, Loss, Draw}
```

### Update player's rating on the base of a game series

The Glicko-2 system suppose that rating is calculated not after every game, 
but after some period of games. The more games in the period - the better. 
Mark Glickman suppose at least 10 games per player in a rating period. 

```scala
val player = Player(1500, 200, 0.06)
val games = List(
  Win(Player(1400, 30, 0.06)),
  Loss(Player(1550, 100, 0.06)),
  Loss(Player(1700, 300, 0.06)))
  
val updatedPlayer = Glicko2.update(player, games)
```     

The `updatedPlayer` will be `Player(1464.05,151.52,0.059996)`

### Update player's rating if player did not play a game in this rating period

Only deviation is changed

```scala
val player = Player(1500, 200, 0.06)
val updatedPlayer = Glicko2.update(player)
```
The `updatedPlayer` will be `Player(1500,200.27,0.06)`

### Creation new user with the default parameters

Create user with the rating parameters for the `Parameters` object. 

```scala
val player = Glicko2.defaultPlayer()
```

The `player` will be `Player(1500,350,0.06)`

### The 95% confidence interval

It's more  informative to show user rating as interval 
with some level of confidence. `Player.ratingLow` and `Player.ratingHight` returns
the borders of 95% rating interval. [Look more in the example paper](http://www.glicko.net/glicko/glicko2.pdf) 

### Tuning Parameters

The default game parameters will be used if none are present 
in the implicit context.

```scala
case class Parameters(
  
  // Game constants.
  tau: Double = 0.5, 
  epsilon: Double = 0.000001,
  scale: Double = 173.7178,
  
  // Default values for the new player.
  defaultRating: Double = 1500,
  defaultDeviation: Double = 350,
  defaultVolatility: Double = 0.06,
  
  // Scale of the result.
  ratingScale: Int = 2,
  deviationScale: Int = 2,
  sigmaScale: Int = 6)
```

Scale is the number of digits after decimal point. 
If you want to receive rating without decimal part, then set `ratingScale` to 0. 

Create new implicit `Parameters` object if you want to change some parameters.

```scala
implicit val parameters = Glicko2.Parameters().copy(
    defaultDeviation = 100, 
    defaultVolatility = 0.05)
    
val player = Glicko2.defaultPlayer()    
```        

The `player` will be `Player(1500,100,0.05)` in this case


## Precision

Test data for the Glicko-2 system verification usually are taken form the 
[Example of the Glicko-2 system](http://www.glicko.net/glicko/glicko2.pdf)

Unfortunately that calculation is just example with a very low precision. 
More accurate calculation, that programs trivially do, 
gives little different result.    

Almost all of the part of calculation use rounding, so we need to remember about
precision digits. 
This is especially actual for the programming with floating points.

In the paper:

```
r` = −0.2069(173.7178) + 1500 = 1464.06  
``` 

Without rounding:

```
r` = −0.2069(173.7178) + 1500 = 1464.05778718
``` 

It rounded to 1464.06 that is correct, but, in program, 
we can receive result like this

```
r` = -0.20694097869815928 * (173.7178) + 1500 = 1464.05066845070891
```

This result rounded to 1464.05. 
 
The calculation in paper has classic error of rounding in the middle of calculation.


``` 
µ` = 0 + (0.8722 * 0.8722) × [0.9955(1 − 0.639) + 0.9531(0 − 0.432) + 0.7242(0 − 0.303)] 
   = 0 + 0.7607(−0.272) 
   = −0.2069
```

Let's do the same calculation with saving of all available middle precision

```
µ` = 0 + 0.76073284 * (0.9955 * 0.361) + (0.9531 * (-0.432)) + (0.7242 * (-0.303)) 
   = 0.76073284 * (0.3593755 - 0.4117392 - 0.2194326) 
   = 0.76073284 * (-0.2717963) 
   = -0.20676437120049

r` = -0.20676437120049 * (173.7178) + 1500 = 1464.08134831666752
```

That is rounded to 1464.08! Far away from the 1464.06. 

The reason to so different results is losing of precision. 
We use rounding in the calculation so we need to remember about precision.
In the paper µ` has precision 3 (multiplication of 0.361, -0.432 ... ), so result of 

```
−0.2069 * (173.7178) = -35.94221282 
```

has also precision 3, as well as the result of

``` 
-0.20676437120049 * (173.7178) = -35.91865168333248
```

It means that we can strictly believe only in one digit after decimal point.
Default rating, 1500, is the exact number, 
i.e. it has infinite precision: 1500.000000 ... Then

``` 
r` = -35.94221282 + 1500.000000 
   = 1464.05778718 
   = 1464.1 
```

So all our calculations is correct, but we can not write 1464.06, 
we must write 1464.1. Unfortunately, numbers in µ` calculation, 
like 0.9955, 0.361 was rounded as well. 

Usually, if you round in the middle of calculation, 
then rounded numbers must be computed with two more significant figures 
than the very final result. 
By this rule, final result of the  calculation that use µ` would have precision 1 
and the very strict answer is 1460. 

We see now, that the 1464.06 is not correct answer. 
Let's find the correct answer with the precision of 6 (2 digits after decimal point in our case)

```
µ` = 0.76073284 * (0.995498 * 0.360532) + (0.953149 * (-0.431842)) + (0.724235 * (-0.302841)) 
   = 0.76073284 * (0.358908884936 - 0.411609770458 - 0.219328051635) 
   = 0.76073284 * -0.272028937157 
   = -0.20694134592563

r` = -0.20694134592563 * 173.7178 + 1500 
   = -35.94939534323941 + 1500 
   = 1464.05060465676059 
   = 1464.05
```
   
The precision of -35.94939534323941 was 6 with 4 digits after decimal point. 
The 1500 is exact number, so addition of -35.94939534323941 and 1500 
will give precision 4 before decimal point and precision 4 after decimal point,
like 1464.0506. 
It is the final answer so we will round off two last significant number 
to receive safe result. 

The correct answer is 1464.05.

The same with RD` calculation, fortunately it  matches a correct value.

σ` is just truncated. The correct answer is 0.0599958431496. 
It's definitely not 0.05999.
It would be 0.06000, If we want to round it to 6 digits after decimal point.
It looks like better to use one more digit to represent this number, 
just to catch changes.

```
σ` = 0.059996
```