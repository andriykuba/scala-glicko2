package com.github.andriykuba.scala.glicko2

import collection.mutable.Stack
import org.scalatest._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import com.github.andriykuba.scala.glicko2.scala.Glicko2System;

import java.util.HashMap
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.github.andriykuba.scala.glicko2.scala.Glicko2System.Win
import com.github.andriykuba.scala.glicko2.scala.Glicko2System.Loss
import com.github.andriykuba.scala.glicko2.scala.Glicko2System.Player

@RunWith(classOf[JUnitRunner])
class Glicko2SystemTest extends FlatSpec with Matchers with MockitoSugar {
  
  "Glicko System" should "create default player" in {
    val player = Glicko2System.defaultPlayer()

    player.rating should be (BigDecimal(1500))
    player.deviation should be (BigDecimal(350))
    player.volatility should be (BigDecimal(0.06))
  }
  
  it should "update rating after series of games" in {
    val games = List(
        Win(Player(1400, 30, 0.06)),
        Loss(Player(1550, 100, 0.06)),
        Loss(Player(1700, 300, 0.06)))
    val player = Player(1500, 200, 0.06)
    val result = Glicko2System.update(player, games)
		
    result.rating should be (BigDecimal(1464.05))
    result.deviation should be (BigDecimal(151.52))
    result.volatility should be (BigDecimal(0.059996))
  }
  
  "it" should "update rating if user did not take a part in games in the period" in {
    val player = Player(1500, 200, 0.06)
    val result = Glicko2System.update(player)
    
    result.rating should be (BigDecimal(1500))
    result.deviation should be (BigDecimal(200.27))
    result.volatility should be (BigDecimal(0.06))
  }

  "it" should "take implicit parameters" in {
    implicit val parameters = Glicko2System.Parameters().copy(
        defaultDeviation = 100, 
        defaultVolatility = 0.05)
        
    val player = Glicko2System.defaultPlayer()
    
    player.rating should be (BigDecimal(1500))
    player.deviation should be (BigDecimal(100))
    player.volatility should be (BigDecimal(0.05))
  }
}